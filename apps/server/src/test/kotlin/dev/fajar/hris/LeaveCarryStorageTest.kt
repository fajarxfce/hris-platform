package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class LeaveCarryStorageTest : LeaveCarryStorageFixture() {
    @Test
    fun legacyCarryAndNullPolicyJsonRetainExistingBehavior() {
        val prepared = prepareCarry(months = null)
        val legacy =
            prepared.copy(
                closing = prepared.closing.copy(carryTracking = false, timezone = null),
                award = null,
            )
        success(storeCarry(legacy))
        val f = legacy.f
        assertEquals(0, accountingRows(f, "leave_carry_buckets"))
        assertFalse(
            database()
                .queryForObject(
                    "select policy_snapshot::text from leave_year_closings where company_id=?",
                    String::class.java,
                    f.company,
                )!!
                .contains("carryValidityMonths")
        )
        assertEquals("3", entitlementView(f, 2027)["balance"]["availableDays"].asString())
    }

    @Test
    fun trackedClosingAndBucketCommitTogetherWithFrozenValidity() {
        val c = carried()
        val bucket = storedBucket(c)
        assertEquals(6, bucket.availableHalfDays)
        assertEquals(carryDeadline, bucket.validity?.expiresAt)
        assertEquals(LocalDate.of(2027, 3, 31), bucket.validity?.through)
        assertEquals(6, storedBalance(c).availableHalfDays)
        val rows =
            success(
                transactions.run(accountingActor(c.f)) {
                    carryRepository.buckets(c.f.company, c.f.employee, c.f.type, setOf(2026, 2027))
                }
            )
        assertEquals(listOf(bucket), rows)
        assertThrows(UnsupportedOperationException::class.java) {
            (rows as MutableList<LeaveCarryBucket>).clear()
        }
        assertEquals(
            c.closing,
            success(
                transactions.run(accountingActor(c.f)) {
                    carryEntitlements.closing(c.f.company, c.f.employee, c.f.type, 2026)
                }
            ),
        )
    }

    @Test
    fun missingAwardRollsBackTheClosingLedgerProjectionAndSyncChanges() {
        val c = prepareCarry()
        val tables =
            listOf(
                "leave_year_closings",
                "leave_accounts",
                "leave_ledger",
                "leave_carry_buckets",
                "mobile_sync_changes",
            )
        val before = tables.associateWith { accountingRows(c.f, it) }
        assertTrue(storeCarry(c, includeAward = false) is Result.Failed)
        assertEquals(before, tables.associateWith { accountingRows(c.f, it) })
        success(storeCarry(c))
        assertEquals(6, storedBucket(c).availableHalfDays)
    }

    @Test
    fun aClosingAfterTheDeadlineCannotCreateAlreadyExpiredAvailableCredit() {
        val c = prepareCarry(at = carryDeadline)
        assertNull(c.award)
        success(storeCarry(c))
        assertEquals(0, accountingRows(c.f, "leave_carry_buckets"))
        assertEquals(0, storedBalance(c).availableHalfDays)
        assertEquals(6, c.closing.rollover.expireHalfDays)
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from leave_ledger where company_id=? and kind='EXPIRE' and year_closing_id=? and carry_expiry_id is null",
                    Int::class.java,
                    c.f.company,
                    c.closing.id,
                ),
        )
    }

    @Test
    fun carryExpiryRequiresItsPairedLedgerProofAndCannotHappenEarly() {
        val c = carried()
        val before = storedBucket(c)
        assertTrue(expiry(c, ledgerIncluded = false) is Result.Failed)
        assertEquals(before, storedBucket(c))
        assertTrue(expiry(c, at = carryDeadline.minusNanos(1_000_000)) is Result.Failed)
        assertEquals(before, storedBucket(c))
        success(expiry(c))
        val after = storedBucket(c)
        assertEquals(6, after.expiredHalfDays)
        assertEquals(0, storedBalance(c).availableHalfDays)
        val entries =
            success(
                transactions.run(accountingActor(c.f)) {
                    carryLedger.entries(c.f.company, c.f.employee, c.f.type, 2027, null, 10)
                }
            )
        val expired = entries.items.single { it.kind == LeaveLedgerKind.EXPIRE }
        assertEquals(expired.sourceId, expired.carryExpiryId)
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from leave_ledger where company_id=? and carry_expiry_id is not null and year_closing_id is not null",
                    Int::class.java,
                    c.f.company,
                ),
        )
    }

    @Test
    fun nullValidityRemainsUnexpiredAndRejectsInventedExpiryProof() {
        val c = carried(months = null)
        assertNull(storedBucket(c).validity)
        val posting =
            LeaveCarryPosting(
                UUID.randomUUID(),
                LeaveCarryMovement(c.closing.id, LeaveCarryEffect.EXPIRE, 6),
                0,
                UUID.randomUUID(),
            )
        val entry =
            LeaveLedgerEntry(
                posting.ledgerEntryId,
                c.f.employee,
                c.f.type,
                2027,
                LeaveLedgerKind.EXPIRE,
                posting.id,
                null,
                -6,
                0,
                0,
                c.closing.actorId,
                carryDeadline,
                "Invalid expiry",
                posting.id,
            )
        val result =
            transactions.run(accountingActor(c.f)) {
                carryRepository.append(c.f.company, listOf(posting)).flatMap {
                    carryLedger.append(c.f.company, listOf(entry))
                }
            }
        assertTrue(result is Result.Failed)
        assertEquals(6, storedBucket(c).availableHalfDays)
    }

    @Test
    fun retainedFundingSupportsLateApprovalAndExpiresALateRefundAtomically() {
        val c = carried()
        configureWorkAndApprovals(c.f)
        val id = success(reserveCarry(c))
        assertEquals(2, storedBucket(c).reservedHalfDays)
        success(expiry(c))
        assertEquals(4, storedBucket(c).expiredHalfDays)
        success(finishCarry(c, id, LeaveBalanceEffect.CONSUME, carryDeadline.plusSeconds(1)))
        assertEquals(2, storedBucket(c).consumedHalfDays)
        assertEquals(2, storedBalance(c).consumedHalfDays)
        val before = storedBucket(c)
        assertTrue(
            finishCarry(
                c,
                id,
                LeaveBalanceEffect.REFUND,
                carryDeadline.plusSeconds(2),
                settleReturned = false,
            )
                is Result.Failed
        )
        assertEquals(before, storedBucket(c))
        success(finishCarry(c, id, LeaveBalanceEffect.REFUND, carryDeadline.plusSeconds(2)))
        assertEquals(6, storedBucket(c).expiredHalfDays)
        assertEquals(0, storedBalance(c).availableHalfDays)
        assertEquals(0, storedBalance(c).consumedHalfDays)
    }

    @Test
    fun earlyWithdrawalRestoresTheOriginalBucketAndLateWithdrawalExpiresIt() {
        val early = carried()
        configureWorkAndApprovals(early.f)
        val earlyId = success(reserveCarry(early))
        success(
            finishCarry(early, earlyId, LeaveBalanceEffect.RELEASE, carryDeadline.minusSeconds(1))
        )
        assertEquals(6, storedBucket(early).availableHalfDays)
        assertEquals(0, storedBucket(early).expiredHalfDays)
        val late = carried()
        configureWorkAndApprovals(late.f)
        val lateId = success(reserveCarry(late))
        success(finishCarry(late, lateId, LeaveBalanceEffect.RELEASE, carryDeadline))
        assertEquals(6, storedBucket(late).expiredHalfDays)
        assertEquals(0, storedBalance(late).availableHalfDays)
    }

    @Test
    fun fundingCannotExceedItsWorkDatesOrAppearAfterAnUnfundedSubmission() {
        val c = carried()
        configureWorkAndApprovals(c.f)
        val before = storedBucket(c)
        assertTrue(reserveCarry(c, date = LocalDate.of(2027, 4, 1)) is Result.Failed)
        assertTrue(reserveCarry(c, units = 3) is Result.Failed)
        assertTrue(reserveCarry(c, fundingCount = 0) is Result.Failed)
        assertEquals(before, storedBucket(c))
        assertEquals(0, accountingRows(c.f, "leave_requests"))
        assertEquals(0, accountingRows(c.f, "leave_carry_allocations"))
    }

    @Test
    fun incompleteFundingRollsBackRequestsApprovalsMovementsAndSyncTogether() {
        val c = carried()
        configureWorkAndApprovals(c.f)
        val tables =
            listOf(
                "leave_requests",
                "approval_requests",
                "leave_allocations",
                "leave_carry_allocations",
                "leave_carry_movements",
                "leave_ledger",
                "mobile_sync_changes",
            )
        val before = tables.associateWith { accountingRows(c.f, it) }
        assertTrue(reserveCarry(c, allocationsIncluded = false) is Result.Failed)
        assertEquals(before, tables.associateWith { accountingRows(c.f, it) })
        assertTrue(reserveCarry(c, ledgerIncluded = false) is Result.Failed)
        assertEquals(before, tables.associateWith { accountingRows(c.f, it) })
        success(reserveCarry(c))
    }

    @Test
    fun completingOnlyTheMainLedgerCannotLoseItsCarryAllocation() {
        val c = carried()
        configureWorkAndApprovals(c.f)
        val id = success(reserveCarry(c))
        val entry =
            LeaveLedgerEntry(
                UUID.randomUUID(),
                c.f.employee,
                c.f.type,
                2027,
                LeaveLedgerKind.CONSUME,
                id,
                id,
                0,
                -2,
                2,
                c.closing.actorId,
                c.closing.recordedAt.plusSeconds(10),
                "Incomplete consumption",
            )
        val result =
            transactions.run(accountingActor(c.f)) {
                carryLedger.append(c.f.company, listOf(entry))
            }
        assertTrue(result is Result.Failed)
        assertEquals(2, storedBucket(c).reservedHalfDays)
        assertEquals(2, storedBalance(c).reservedHalfDays)
        assertEquals(0, storedBalance(c).consumedHalfDays)
    }

    @Test
    fun carryMetadataAndFundingAreImmutableAndCompanyScoped() {
        val c = carried()
        configureWorkAndApprovals(c.f)
        val id = success(reserveCarry(c))
        for (table in
            listOf("leave_carry_buckets", "leave_carry_allocations", "leave_carry_movements")) {
            assertThrows(DataAccessException::class.java) {
                database().update("delete from $table where company_id=?", c.f.company)
            }
            assertEquals(
                Result.Success(0),
                transactions.run(accountingActor(c.f).copy(companyId = UUID.randomUUID())) {
                    safeDatabaseCall {
                        runtimeJdbc.queryForObject("select count(*) from $table", Int::class.java)
                    }
                },
            )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_carry_buckets set version=version+1,available_half_days=available_half_days-1,deducted_half_days=deducted_half_days+1 where company_id=?",
                    c.f.company,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_requests set carry_allocation_count=0,version=version+1 where company_id=? and id=?",
                    c.f.company,
                    id,
                )
        }
        val other = accountingActor(c.f).copy(companyId = UUID.randomUUID())
        assertEquals(
            Result.Success(emptyList<LeaveCarryBucket>()),
            transactions.run(other) {
                carryRepository.buckets(c.f.company, c.f.employee, c.f.type, setOf(2027))
            },
        )
        assertEquals(
            Result.Success(emptyList<LeaveCarryAllocation>()),
            transactions.run(other) { carryRepository.allocations(c.f.company, id) },
        )
        val snapshot = storedBucket(c)
        val forged =
            LeaveCarryPosting(
                UUID.randomUUID(),
                LeaveCarryMovement(snapshot.id, LeaveCarryEffect.DEDUCT, 1),
                snapshot.version,
                UUID.randomUUID(),
            )
        assertTrue(
            transactions.run(other) { carryRepository.append(c.f.company, listOf(forged)) }
                is Result.Failed
        )
        assertEquals(snapshot, storedBucket(c))
    }

    @Test
    fun carryCannotExceedTheMainAccountAfterAnUnattributedDebit() {
        val c = carried()
        val entry =
            LeaveLedgerEntry(
                UUID.randomUUID(),
                c.f.employee,
                c.f.type,
                2027,
                LeaveLedgerKind.ADJUSTMENT,
                UUID.randomUUID(),
                null,
                -1,
                0,
                0,
                c.closing.actorId,
                c.closing.recordedAt,
                "Missing carry deduction",
            )
        assertTrue(
            transactions.run(accountingActor(c.f)) {
                carryLedger.append(c.f.company, listOf(entry))
            } is Result.Failed
        )
        assertEquals(6, storedBalance(c).availableHalfDays)
        val posting =
            LeaveCarryPosting(
                UUID.randomUUID(),
                LeaveCarryMovement(c.closing.id, LeaveCarryEffect.DEDUCT, 1),
                0,
                entry.id,
            )
        success(
            transactions.run(accountingActor(c.f)) {
                carryRepository.append(c.f.company, listOf(posting)).flatMap {
                    carryLedger.append(c.f.company, listOf(entry))
                }
            }
        )
        assertEquals(1, storedBucket(c).deductedHalfDays)
        assertEquals(5, storedBalance(c).availableHalfDays)
    }

    @Test
    fun competingExpiryTransactionsCannotSpendTheSameBucketVersionTwice() {
        val c = carried()
        val snapshot = storedBucket(c)
        val acquired = CountDownLatch(1)
        val release = CountDownLatch(1)
        val waiting = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val first =
                executor.submit<Result<Unit>> {
                    expiry(
                        c,
                        bucket = snapshot,
                        afterLock = {
                            acquired.countDown()
                            check(release.await(10, TimeUnit.SECONDS))
                        },
                    )
                }
            try {
                assertTrue(acquired.await(10, TimeUnit.SECONDS))
                val second =
                    executor.submit<Result<Unit>> {
                        waiting.countDown()
                        expiry(c, bucket = snapshot)
                    }
                assertTrue(waiting.await(10, TimeUnit.SECONDS))
                release.countDown()
                success(first.get(20, TimeUnit.SECONDS))
                assertTrue(second.get(20, TimeUnit.SECONDS) is Result.Failed)
            } finally {
                release.countDown()
            }
        }
        assertEquals(6, storedBucket(c).expiredHalfDays)
        assertEquals(1, accountingRows(c.f, "leave_carry_movements"))
        assertEquals(0, storedBalance(c).availableHalfDays)
    }
}
