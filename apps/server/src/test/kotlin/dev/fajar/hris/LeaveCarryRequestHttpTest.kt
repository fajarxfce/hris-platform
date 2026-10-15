package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

class LeaveCarryRequestHttpTest : LeaveCarryStorageFixture() {
    private fun prepared(staged: Boolean = false): PreparedCarry =
        carried().also { configureWorkAndApprovals(it.f, staged) }

    private fun preview(c: PreparedCarry, days: List<Pair<String, String>>): HttpResponse<String> =
        command(
            c.f.worker,
            "/api/v1/companies/${c.f.company}/leave/me/requests/preview",
            json.writeValueAsString(
                mapOf(
                    "employeeId" to c.f.employee,
                    "typeId" to c.f.type,
                    "days" to days.map { mapOf("workDate" to it.first, "portion" to it.second) },
                )
            ),
            c.f.workerCsrf,
            UUID.randomUUID(),
        )

    private fun fundOrdinary(c: PreparedCarry, units: Int) {
        success(
            transactions.run(accountingActor(c.f)) {
                carryLedger.lock(c.f.company, c.f.employee).flatMap {
                    val id = UUID.randomUUID()
                    carryLedger.append(
                        c.f.company,
                        listOf(
                            LeaveLedgerEntry(
                                id,
                                c.f.employee,
                                c.f.type,
                                2027,
                                LeaveLedgerKind.ADJUSTMENT,
                                id,
                                null,
                                units,
                                0,
                                0,
                                c.closing.actorId,
                                clock.instant(),
                                "Ordinary credit fixture",
                            )
                        ),
                    )
                }
            }
        )
    }

    private fun renew(c: PreparedCarry, at: Instant): PreparedCarry {
        clock.set(at)
        val f = c.f
        return c.copy(
            f =
                f.copy(
                    workerCsrf = login(f.worker, "${f.account}@example.test"),
                    supervisorCsrf = login(f.supervisor, "${f.managerAccount}@example.test"),
                    adminCsrf = login(f.admin),
                )
        )
    }

    private fun funding(c: PreparedCarry, id: UUID): List<LeaveCarryAllocation> =
        success(
            transactions.run(accountingActor(c.f)) { carryRepository.allocations(c.f.company, id) }
        )

    @Test
    fun reviewAndSubmissionRetainHalfDayCarryFundingAndOriginalReceipt() {
        val c = prepared()
        val days = listOf("2027-03-15" to "FULL", "2027-03-16" to "FIRST_HALF")
        val reviewed = accountingBody(preview(c, days))
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val result =
            submit(c.f, id, days, operation, previewRevision = reviewed["revision"].asString())
        accountingBody(result)
        assertEquals(
            result.body(),
            submit(c.f, id, days, operation, previewRevision = reviewed["revision"].asString())
                .body(),
        )
        assertEquals(listOf(LeaveCarryAllocation(c.closing.id, 2027, 3)), funding(c, id))
        assertEquals(3, storedBucket(c).availableHalfDays)
        assertEquals(3, storedBucket(c).reservedHalfDays)
        assertEquals(3, storedBalance(c).reservedHalfDays)
        assertEquals(1, accountingRows(c.f, "leave_carry_movements"))
    }

    @Test
    fun datesBeyondValidityUseOnlyOrdinaryCreditBeforeTheDeadline() {
        val c = prepared()
        val days = listOf("2027-04-05" to "FULL")
        accountingCode(preview(c, days), 409, "insufficient_leave_balance")
        accountingCode(submit(c.f, UUID.randomUUID(), days), 409, "insufficient_leave_balance")
        fundOrdinary(c, 2)
        val revision = accountingBody(preview(c, days))["revision"].asString()
        val id = UUID.randomUUID()
        accountingBody(submit(c.f, id, days, previewRevision = revision))
        assertTrue(funding(c, id).isEmpty())
        assertEquals(6, storedBucket(c).availableHalfDays)
        assertEquals(0, storedBucket(c).reservedHalfDays)
        assertEquals(2, storedBalance(c).reservedHalfDays)
    }

    @Test
    fun crossingValidityRequiresFreshReviewEvenWhenOrdinaryFundsCanCoverTheRequest() {
        val c =
            prepareCarry(at = carryDeadline.minusSeconds(60)).also {
                success(storeCarry(it))
                configureWorkAndApprovals(it.f)
            }
        fundOrdinary(c, 2)
        val days = listOf("2027-03-31" to "FULL")
        val original = accountingBody(preview(c, days))["revision"].asString()
        val late = renew(c, carryDeadline)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        accountingCode(
            submit(late.f, id, days, key, previewRevision = original),
            409,
            "leave_preview_changed",
        )
        assertEquals(0, accountingRows(late.f, "leave_requests"))
        val fresh = accountingBody(preview(late, days))["revision"].asString()
        assertNotEquals(original, fresh)
        accountingBody(submit(late.f, id, days, key, previewRevision = fresh))
        assertTrue(funding(late, id).isEmpty())
        assertEquals(6, storedBucket(late).availableHalfDays)
        assertEquals(0, storedBucket(late).reservedHalfDays)
    }

    @Test
    fun anUnsettledExpiredBucketCannotFundAnEarlierLeaveDate() {
        val late = renew(prepared(), carryDeadline)
        val days = listOf("2027-03-31" to "FULL")
        accountingCode(preview(late, days), 409, "insufficient_leave_balance")
        accountingCode(submit(late.f, UUID.randomUUID(), days), 409, "insufficient_leave_balance")
        assertEquals(0, accountingRows(late.f, "leave_requests"))
        assertEquals(6, storedBucket(late).availableHalfDays)
    }

    @Test
    fun lateApprovalConsumesTheOriginalReservationAndCancellationExpiresItsRefund() {
        val c = prepared()
        val id = UUID.randomUUID()
        accountingBody(submit(c.f, id, listOf("2027-03-15" to "FULL")))
        val late = renew(c, carryDeadline.plusSeconds(1))
        accountingBody(decide(late.f, id, 0))
        assertEquals(2, storedBucket(late).consumedHalfDays)
        assertEquals(4, storedBucket(late).expiredHalfDays)
        assertEquals(0, storedBalance(late).availableHalfDays)
        accountingBody(action(late.f, id, "cancellation", 1))
        val key = UUID.randomUUID()
        val cancelled = decide(late.f, id, 2, key = key)
        accountingBody(cancelled)
        assertEquals(cancelled.body(), decide(late.f, id, 2, key = key).body())
        assertEquals("CANCELLED", details(late.f, id)["status"].asString())
        assertEquals(6, storedBucket(late).expiredHalfDays)
        assertEquals(0, storedBucket(late).consumedHalfDays)
        assertEquals(0, storedBalance(late).availableHalfDays)
        assertEquals(0, storedBalance(late).consumedHalfDays)
    }

    @Test
    fun withdrawingOrRejectingAfterValidityCannotTurnCarryIntoOrdinaryCredit() {
        for (withdraw in listOf(true, false)) {
            val c = prepared()
            val id = UUID.randomUUID()
            accountingBody(submit(c.f, id, listOf("2027-03-15" to "FULL")))
            val late = renew(c, carryDeadline)
            val key = UUID.randomUUID()
            val response =
                if (withdraw) action(late.f, id, "withdraw", 0, key)
                else decide(late.f, id, 0, "REJECT", key)
            accountingBody(response)
            val replay =
                if (withdraw) action(late.f, id, "withdraw", 0, key)
                else decide(late.f, id, 0, "REJECT", key)
            assertEquals(response.body(), replay.body())
            assertEquals(6, storedBucket(late).expiredHalfDays)
            assertEquals(0, storedBalance(late).availableHalfDays)
            assertEquals(0, storedBalance(late).reservedHalfDays)
            assertEquals(0, accountingRows(late.f, "leave_allocations"))
        }
    }

    @Test
    fun stagedReviewAndWithdrawalOfACancellationDoNotMoveFundingPrematurely() {
        val c = prepared(staged = true)
        val id = UUID.randomUUID()
        accountingBody(submit(c.f, id, listOf("2027-03-15" to "FULL")))
        accountingBody(decide(c.f, id, 0))
        assertEquals(2, storedBucket(c).reservedHalfDays)
        assertEquals(0, storedBucket(c).consumedHalfDays)
        accountingBody(decide(c.f, id, 1, asAdmin = true))
        assertEquals(2, storedBucket(c).consumedHalfDays)
        accountingBody(action(c.f, id, "cancellation", 2))
        val before = storedBucket(c)
        accountingBody(action(c.f, id, "withdraw", 3))
        assertEquals(before, storedBucket(c))
        assertEquals("APPROVED", details(c.f, id)["status"].asString())
    }

    @Test
    fun competingSubmissionsCannotOverspendCarryOrCreateUnfundedReservations() {
        val c = prepared()
        val choices =
            listOf(
                listOf("2027-03-15" to "FULL", "2027-03-16" to "FULL"),
                listOf("2027-03-17" to "FULL", "2027-03-18" to "FULL"),
            )
        val replies =
            Executors.newFixedThreadPool(2).use { executor ->
                val pending =
                    choices.map { days ->
                        executor.submit<HttpResponse<String>> {
                            submit(c.f, UUID.randomUUID(), days)
                        }
                    }
                pending.map { it.get(20, TimeUnit.SECONDS) }
            }
        assertEquals(listOf(200, 409), replies.map { it.statusCode() }.sorted())
        assertEquals(
            "insufficient_leave_balance",
            json.readTree(replies.single { it.statusCode() == 409 }.body())["code"].asString(),
        )
        assertEquals(4, storedBucket(c).reservedHalfDays)
        assertEquals(2, storedBucket(c).availableHalfDays)
        assertEquals(1, accountingRows(c.f, "leave_requests"))
        assertEquals(1, accountingRows(c.f, "leave_carry_allocations"))
    }

    @Test
    fun aLateFailureRollsBackCarryMainLedgerApprovalAndOriginalCommandTogether() {
        val c = prepared()
        val tables =
            listOf(
                "leave_requests",
                "leave_allocations",
                "leave_carry_allocations",
                "leave_carry_movements",
                "leave_ledger",
                "approval_requests",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )
        val before = tables.associateWith { accountingRows(c.f, it) }
        val bucket = storedBucket(c)
        val key = UUID.randomUUID()
        val id = UUID.randomUUID()
        val days = listOf("2027-03-15" to "FULL")
        accountingProbe.beforeJournal = {
            if (it.action == "leave.request_submitted")
                throw DataIntegrityViolationException("Carry transaction fixture")
        }
        assertEquals(409, submit(c.f, id, days, key).statusCode())
        assertEquals(before, tables.associateWith { accountingRows(c.f, it) })
        assertEquals(bucket, storedBucket(c))
        accountingProbe.clear()
        accountingBody(submit(c.f, id, days, key))
        assertEquals(2, storedBucket(c).reservedHalfDays)
    }
}
