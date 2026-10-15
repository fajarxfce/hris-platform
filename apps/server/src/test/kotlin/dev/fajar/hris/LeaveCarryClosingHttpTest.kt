package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.usecases.AdvanceLeaveYearCloseBatch
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException

@Import(LeaveBatchProbeConfiguration::class)
class LeaveCarryClosingHttpTest : LeaveCarryStorageFixture() {
    @Autowired private lateinit var advanceCarryClosing: AdvanceLeaveYearCloseBatch
    @Autowired private lateinit var batchProbe: LeaveBatchProbe
    private val batchCompanies = mutableSetOf<UUID>()

    @AfterEach
    fun release() {
        batchProbe.clear()
        for (company in batchCompanies) database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where company_id=? and status in ('QUEUED','RUNNING')",
                company,
            )
    }

    private fun terms(c: PreparedCarry): LeaveType =
        success(
            transactions.run(accountingActor(c.f)) { carryPolicies.find(c.f.company, c.f.type) }
        )!!

    private fun ordinary(c: PreparedCarry) {
        accountingBody(
            command(
                c.f.admin,
                "/api/v1/companies/${c.f.company}/leave/employees/${c.f.employee}/balances/${c.f.type}/2027/adjustments",
                json.writeValueAsString(
                    mapOf(
                        "days" to "2",
                        "expectedVersion" to storedBalance(c).version,
                        "reason" to "New year ordinary credit",
                    )
                ),
                c.f.adminCsrf,
                UUID.randomUUID(),
            )
        )
    }

    private fun ended(
        c: PreparedCarry,
        at: Instant = Instant.parse("2028-01-05T03:00:00Z"),
    ): PreparedCarry {
        clock.set(at)
        return c.copy(f = c.f.copy(adminCsrf = login(c.f.admin)))
    }

    private fun reviewed(c: PreparedCarry, id: UUID = UUID.randomUUID()): String =
        closingBody(id, storedBalance(c).version, terms(c).version)

    private fun closing(c: PreparedCarry): LeaveYearClosing? =
        success(
            transactions.run(accountingActor(c.f)) {
                carryEntitlements.closing(c.f.company, c.f.employee, c.f.type, 2027)
            }
        )

    private fun award(c: PreparedCarry): LeaveCarryBucket? =
        success(
                transactions.run(accountingActor(c.f)) {
                    carryRepository.buckets(c.f.company, c.f.employee, c.f.type, setOf(2028))
                }
            )
            .singleOrNull()

    @Test
    fun publicClosingCreatesTrackedCreditWithoutInventingOptionalValidity() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"), carry = "3")
        accountingBody(adjust(f, "3"))
        accountingBody(closeYear(f))
        val bucket =
            success(
                    transactions.run(accountingActor(f)) {
                        carryRepository.buckets(f.company, f.employee, f.type, setOf(2027))
                    }
                )
                .single()
        assertEquals(6, bucket.availableHalfDays)
        assertNull(bucket.validity)
        val closed =
            success(
                transactions.run(accountingActor(f)) {
                    carryEntitlements.closing(f.company, f.employee, f.type, 2026)
                }
            )!!
        assertTrue(closed.carryTracking)
        assertEquals("Asia/Jakarta", closed.timezone)
        assertNull(closed.batchId)
    }

    @Test
    fun oldExpiredCreditIsSettledBeforeTheNewClosingSnapshotAndReceipt() {
        val original = carried()
        ordinary(original)
        val c = ended(original)
        val before = storedBalance(c)
        val body = reviewed(c)
        val key = UUID.randomUUID()
        val response = closeYear(c.f, body, key, year = 2027)
        accountingBody(response)
        assertEquals(response.body(), closeYear(c.f, body, key, year = 2027).body())
        val closed = closing(c)!!
        assertEquals(before.version + 1, closed.sourceVersion)
        assertEquals(4, closed.availableHalfDays)
        assertEquals(LeaveYearRollover(4, 0), closed.rollover)
        assertEquals(6, storedBucket(c).expiredHalfDays)
        assertTrue(storedBalance(c).closed)
        assertEquals(4, award(c)!!.availableHalfDays)
        assertEquals(Instant.parse("2028-03-31T17:00:00Z"), award(c)!!.validity!!.expiresAt)
    }

    @Test
    fun unlimitedOldCarryTransfersAndExpiresExplicitlyWithoutChangingItsHistory() {
        val original = carried(months = null)
        ordinary(original)
        success(
            transactions.run(accountingActor(original.f)) {
                success(carryPolicies.lock(original.f.company))
                val type = success(carryPolicies.find(original.f.company, original.f.type))!!
                carryPolicies.save(
                    accountingActor(original.f),
                    type.copy(
                        policy =
                            type.policy.copy(
                                accrual = type.policy.accrual!!.copy(carryLimitHalfDays = 4)
                            )
                    ),
                    type.version,
                    "Reviewed new carry limit",
                )
            }
        )
        val c = ended(original)
        accountingBody(closeYear(c.f, reviewed(c), year = 2027))
        assertEquals(LeaveYearRollover(4, 6), closing(c)!!.rollover)
        assertEquals(4, storedBucket(c).transferredHalfDays)
        assertEquals(2, storedBucket(c).expiredHalfDays)
        assertEquals(0, storedBucket(c).availableHalfDays)
        assertEquals(4, award(c)!!.availableHalfDays)
        assertNull(award(c)!!.validity)
    }

    @Test
    fun lateClosingCannotCreateAnAlreadyExpiredDestinationAward() {
        val original = carried()
        ordinary(original)
        val c = ended(original, Instant.parse("2028-03-31T17:00:00Z"))
        accountingBody(closeYear(c.f, reviewed(c), year = 2027))
        assertEquals(LeaveYearRollover(0, 4), closing(c)!!.rollover)
        assertEquals(6, storedBucket(c).expiredHalfDays)
        assertNull(award(c))
        assertTrue(storedBalance(c).closed)
    }

    @Test
    fun pendingRequestsPreventBothClosingAndItsPreparatoryExpiry() {
        val original = carried()
        configureWorkAndApprovals(original.f)
        accountingBody(submit(original.f, UUID.randomUUID(), listOf("2027-03-15" to "FULL")))
        val c = ended(original)
        val bucket = storedBucket(c)
        val balance = storedBalance(c)
        accountingCode(closeYear(c.f, reviewed(c), year = 2027), 409, "leave_resolution_required")
        assertEquals(bucket, storedBucket(c))
        assertEquals(balance, storedBalance(c))
        assertNull(closing(c))
    }

    @Test
    fun staleReviewAndLateJournalFailureCannotCommitExpiryOrClosing() {
        val original = carried()
        ordinary(original)
        val c = ended(original)
        val balance = storedBalance(c)
        val bucket = storedBucket(c)
        accountingCode(
            closeYear(
                c.f,
                closingBody(sourceVersion = balance.version - 1, policyVersion = terms(c).version),
                year = 2027,
            ),
            409,
            "stale_balance_version",
        )
        accountingCode(
            closeYear(
                c.f,
                closingBody(
                    sourceVersion = balance.version,
                    policyVersion = terms(c).version,
                    destinationVersion = 8,
                ),
                year = 2027,
            ),
            409,
            "stale_destination_balance_version",
        )
        val body = reviewed(c)
        val key = UUID.randomUUID()
        val tables =
            listOf(
                "leave_ledger",
                "leave_year_closings",
                "leave_carry_buckets",
                "leave_carry_movements",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )
        val before = tables.associateWith { accountingRows(c.f, it) }
        accountingProbe.beforeJournal = {
            if (it.action == "leave.year_closed")
                throw DataIntegrityViolationException("Closing fixture")
        }
        try {
            assertEquals(409, closeYear(c.f, body, key, year = 2027).statusCode())
            assertEquals(before, tables.associateWith { accountingRows(c.f, it) })
            assertEquals(balance, storedBalance(c))
            assertEquals(bucket, storedBucket(c))
        } finally {
            accountingProbe.clear()
        }
        accountingBody(closeYear(c.f, body, key, year = 2027))
        assertEquals(6, storedBucket(c).expiredHalfDays)
        assertEquals(4, award(c)!!.availableHalfDays)
    }

    @Test
    fun batchRetainsReviewedTimezoneAndRollsBackWhenItsOutcomeIsMissing() {
        val original = carried()
        ordinary(original)
        val c = ended(original)
        val id = UUID.randomUUID()
        batchCompanies += c.f.company
        accountingBody(
            command(
                c.f.admin,
                "/api/v1/companies/${c.f.company}/leave/year-close-batches",
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "typeId" to c.f.type,
                        "year" to 2027,
                        "expectedPolicyVersion" to terms(c).version,
                        "employeeIds" to setOf(c.f.employee),
                        "reason" to "Reviewed company closing",
                    )
                ),
                c.f.adminCsrf,
                UUID.randomUUID(),
            )
        )
        val lease =
            claimDocuments(JobKind.LEAVE_YEAR_CLOSE).single {
                it.job.request.values["batchId"] == id.toString()
            }
        database().update("update companies set timezone='UTC' where id=?", c.f.company)
        val bucket = storedBucket(c)
        val balance = storedBalance(c)
        batchProbe.omitResult = true
        try {
            assertTrue(advanceCarryClosing.execute(accountingActor(c.f), lease) is Result.Failed)
            assertEquals(balance, storedBalance(c))
            assertEquals(bucket, storedBucket(c))
            assertNull(closing(c))
            assertNull(award(c))
            assertEquals(0, accountingRows(c.f, "leave_batch_results"))
        } finally {
            batchProbe.clear()
        }
        assertFalse(success(advanceCarryClosing.execute(accountingActor(c.f), lease)).finished)
        assertTrue(success(advanceCarryClosing.execute(accountingActor(c.f), lease)).finished)
        assertEquals(id, closing(c)!!.batchId)
        assertEquals("Asia/Jakarta", closing(c)!!.timezone)
        assertEquals(Instant.parse("2028-03-31T17:00:00Z"), award(c)!!.validity!!.expiresAt)
        assertEquals(6, storedBucket(c).expiredHalfDays)
        assertEquals(1, accountingRows(c.f, "leave_batch_results"))
    }
}
