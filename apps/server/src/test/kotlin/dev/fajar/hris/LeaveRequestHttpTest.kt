package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class LeaveRequestHttpTest : LeaveApiFixture() {
    @Test
    fun snapshotsSurvivePolicyAndRosterChangesAndCancellationRefundsOnlyAfterApproval() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "5").statusCode())
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val days = listOf("2026-10-05" to "FULL", "2026-10-06" to "FULL")
        val submitted = submit(f, id, days, key)
        assertEquals(200, submitted.statusCode(), submitted.body())
        assertEquals("3", balance(f).get("availableDays").asString())
        assertEquals("2", balance(f).get("reservedDays").asString())
        assertEquals(200, policy(f, 0, paid = false, active = false).statusCode())
        val off =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/workforce/employees/${f.employee}/roster/2026-10-05",
                """{"reason":"Roster change"}""",
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, off.statusCode(), off.body())
        assertEquals(submitted.body(), submit(f, id, days.reversed(), key).body())
        val snapshot = details(f, id)
        assertTrue(snapshot.get("policy").get("paid").asBoolean())
        assertEquals(0, snapshot.get("policy").get("revision").asLong())
        assertEquals(2, snapshot.get("days").size())
        assertEquals("2026-10-05T15:00:00Z", snapshot.get("days")[0].get("startsAt").asString())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.team.read'",
                f.company,
                f.managerAccount,
            )
        assertTrue(
            details(f, id, f.supervisor).get("availableActions").toString().contains("DECIDE")
        )
        assertFalse(details(f, id).get("availableActions").toString().contains("DECIDE"))
        val approved = decide(f, id, 0)
        assertEquals(200, approved.statusCode(), approved.body())
        assertEquals("2", balance(f).get("consumedDays").asString())
        assertEquals("0", balance(f).get("reservedDays").asString())
        val cancellation = action(f, id, "cancellation", 1)
        assertEquals(200, cancellation.statusCode(), cancellation.body())
        assertEquals("2", balance(f).get("consumedDays").asString())
        assertEquals("CANCELLATION_PENDING", details(f, id).get("status").asString())
        val decisionKey = UUID.randomUUID()
        val cancelled = decide(f, id, 2, key = decisionKey)
        assertEquals(200, cancelled.statusCode(), cancelled.body())
        assertEquals(cancelled.body(), decide(f, id, 2, key = decisionKey).body())
        assertEquals("CANCELLED", details(f, id).get("status").asString())
        assertEquals("5", balance(f).get("availableDays").asString())
        assertEquals("0", balance(f).get("consumedDays").asString())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from leave_allocations where request_id=?",
                    Int::class.java,
                    id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from leave_ledger where request_id=? and kind='REFUND'",
                    Int::class.java,
                    id,
                ),
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_requests set reason='overwrite',version=version+1 where id=?",
                    id,
                )
        }
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.team.approve'",
                f.company,
                f.managerAccount,
            )
        assertEquals(
            404,
            get(f.supervisor, "/api/v1/companies/${f.company}/leave/requests/$id").statusCode(),
        )
        val approvalId = snapshot.get("approval").get("id").asString()
        assertEquals(
            404,
            get(f.supervisor, "/api/v1/companies/${f.company}/approvals/$approvalId").statusCode(),
        )
    }

    @Test
    fun simultaneousRequestsCannotReserveTheSameAvailableBalance() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "2").statusCode())
        val ids = List(2) { UUID.randomUUID() }
        val keys = List(2) { UUID.randomUUID() }
        val dates =
            listOf(
                listOf("2026-10-05" to "FULL", "2026-10-06" to "FULL"),
                listOf("2026-10-07" to "FULL", "2026-10-08" to "FULL"),
            )
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val statuses =
            Executors.newFixedThreadPool(2).use { pool ->
                val results =
                    (0..1).map { index ->
                        pool.submit<Int> {
                            ready.countDown()
                            assertTrue(start.await(5, TimeUnit.SECONDS))
                            submit(f, ids[index], dates[index], keys[index]).statusCode()
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                results.map { it.get(15, TimeUnit.SECONDS) }
            }
        assertEquals(listOf(200, 409), statuses.sorted())
        assertEquals("0", balance(f).get("availableDays").asString())
        assertEquals("2", balance(f).get("reservedDays").asString())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from leave_requests where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from approval_requests where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            4,
            database()
                .queryForObject(
                    "select count(*) from leave_allocations where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(200, adjust(f, "2").statusCode())
        val failed = statuses.indexOf(409)
        val retried = submit(f, ids[failed], dates[failed], keys[failed])
        assertEquals(200, retried.statusCode(), retried.body())
        assertEquals("4", balance(f).get("reservedDays").asString())
    }

    @Test
    fun halfDayOccupancyIsSharedAcrossTypesAndWithdrawalReleasesOnce() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "2").statusCode())
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        assertEquals(200, submit(f, first, listOf("2026-10-05" to "FIRST_HALF")).statusCode())
        assertEquals(200, submit(f, second, listOf("2026-10-05" to "SECOND_HALF")).statusCode())
        val other = f.copy(type = UUID.randomUUID())
        assertEquals(200, policy(other, code = "OTHER").statusCode())
        assertEquals(200, adjust(other, "1").statusCode())
        val overlap = submit(other, UUID.randomUUID(), listOf("2026-10-05" to "FULL"))
        assertEquals(409, overlap.statusCode(), overlap.body())
        assertEquals("leave_overlap", json.readTree(overlap.body()).get("code").asString())
        val key = UUID.randomUUID()
        val withdrawn = action(f, first, "withdraw", 0, key)
        assertEquals(200, withdrawn.statusCode(), withdrawn.body())
        assertEquals(withdrawn.body(), action(f, first, "withdraw", 0, key).body())
        assertEquals(409, submit(f, UUID.randomUUID(), listOf("2026-10-05" to "FULL")).statusCode())
        assertEquals(200, action(f, second, "withdraw", 0).statusCode())
        assertEquals("2", balance(f).get("availableDays").asString())
        assertEquals(200, submit(f, UUID.randomUUID(), listOf("2026-10-05" to "FULL")).statusCode())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from leave_ledger where company_id=? and kind='RELEASE'",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun decisionsAreAtomicWithLedgerAndAllocationCleanup() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "4").statusCode())
        val first = UUID.randomUUID()
        assertEquals(200, submit(f, first, listOf("2026-10-05" to "FULL")).statusCode())
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        decide(f, first, 0).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(listOf(200, 409), results.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals("1", balance(f).get("consumedDays").asString())
        val second = UUID.randomUUID()
        assertEquals(200, submit(f, second, listOf("2026-10-06" to "FULL")).statusCode())
        database()
            .execute(
                """CREATE FUNCTION test_block_leave_cleanup() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF OLD.request_id='$second'::uuid THEN RAISE EXCEPTION 'deliberate cleanup failure' USING ERRCODE='23514'; END IF; RETURN OLD; END $$"""
            )
        database()
            .execute(
                "CREATE TRIGGER test_cleanup_failure BEFORE DELETE ON leave_allocations FOR EACH ROW EXECUTE FUNCTION test_block_leave_cleanup()"
            )
        val key = UUID.randomUUID()
        try {
            val failed = decide(f, second, 0, "REJECT", key)
            assertEquals(409, failed.statusCode(), failed.body())
            assertFalse(failed.body().contains("deliberate"))
            assertEquals("PENDING", details(f, second).get("status").asString())
            assertEquals(0, details(f, second).get("approval").get("version").asLong())
            assertEquals("1", balance(f).get("reservedDays").asString())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from leave_ledger where request_id=? and kind='RELEASE'",
                        Int::class.java,
                        second,
                    ),
            )
        } finally {
            database().execute("DROP TRIGGER test_cleanup_failure ON leave_allocations")
            database().execute("DROP FUNCTION test_block_leave_cleanup()")
        }
        val retried = decide(f, second, 0, "REJECT", key)
        assertEquals(200, retried.statusCode(), retried.body())
        assertEquals("REJECTED", details(f, second).get("status").asString())
        assertEquals("3", balance(f).get("availableDays").asString())
    }

    @Test
    fun stagedApprovalsAndWithdrawnOrRejectedCancellationsKeepConsumption() {
        val f = leaveFixture()
        configureWorkAndApprovals(f, staged = true)
        assertEquals(200, adjust(f, "1").statusCode())
        val id = UUID.randomUUID()
        assertEquals(200, submit(f, id, listOf("2026-10-05" to "FULL")).statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.team.approve')",
                f.company,
                f.account,
            )
        val self =
            command(
                f.worker,
                "/api/v1/companies/${f.company}/leave/requests/$id/decisions",
                """{"version":0,"decision":"APPROVE"}""",
                f.workerCsrf,
                UUID.randomUUID(),
            )
        assertEquals(403, self.statusCode(), self.body())
        assertEquals("self_approval_denied", json.readTree(self.body()).get("code").asString())
        assertEquals(200, decide(f, id, 0).statusCode())
        assertEquals("PENDING", details(f, id).get("status").asString())
        assertEquals("1", balance(f).get("reservedDays").asString())
        assertEquals(200, decide(f, id, 1, asAdmin = true).statusCode())
        assertEquals("1", balance(f).get("consumedDays").asString())
        assertEquals(200, action(f, id, "cancellation", 2).statusCode())
        assertEquals(200, action(f, id, "withdraw", 3).statusCode())
        assertEquals("APPROVED", details(f, id).get("status").asString())
        assertEquals("1", balance(f).get("consumedDays").asString())
        assertEquals(200, action(f, id, "cancellation", 4).statusCode())
        assertEquals(200, decide(f, id, 5, "REJECT").statusCode())
        assertEquals("1", balance(f).get("consumedDays").asString())
        assertEquals(200, action(f, id, "cancellation", 6).statusCode())
        assertEquals(200, decide(f, id, 7).statusCode())
        assertEquals("1", balance(f).get("availableDays").asString())
        assertEquals(0, details(f, id).get("availableActions").size())
        val history = details(f, id, suffix = "?historyLimit=2").get("history")
        assertEquals("7", history.get("nextCursor").asString())
        assertEquals("DECIDED", history.get("items")[0].get("kind").asString())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from leave_ledger where request_id=? and kind='REFUND'",
                    Int::class.java,
                    id,
                ),
        )
    }

    @Test
    fun yearSpanningRequestsAllocateBothBucketsAndRejectNewIneligibleDates() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "1").statusCode())
        val nextYear =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/leave/employees/${f.employee}/balances/${f.type}/2027/adjustments",
                """{"days":"1","reason":"Next year entitlement"}""",
                f.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, nextYear.statusCode(), nextYear.body())
        val id = UUID.randomUUID()
        val days = listOf("2026-12-31" to "FULL", "2027-01-01" to "FULL")
        val response = submit(f, id, days)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals(200, decide(f, id, 0).statusCode())
        assertEquals("1", balance(f).get("consumedDays").asString())
        val secondBalance =
            get(
                f.worker,
                "/api/v1/companies/${f.company}/leave/employees/${f.employee}/balances/${f.type}/2027",
            )
        assertEquals(
            "1",
            json.readTree(secondBalance.body()).get("balance").get("consumedDays").asString(),
        )
        val ended =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees/${f.employee}/revisions",
                json.writeValueAsString(
                    mapOf(
                        "version" to 0,
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2027-01-01",
                                "startDate" to "2025-01-01",
                                "endDate" to "2026-12-31",
                                "status" to "ENDED",
                                "contract" to "PERMANENT",
                            ),
                        "reason" to "Scheduled separation",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, ended.statusCode(), ended.body())
        val ineligible = submit(f, UUID.randomUUID(), days)
        assertEquals(422, ineligible.statusCode(), ineligible.body())
        assertEquals(
            "leave_employee_ineligible",
            json.readTree(ineligible.body()).get("code").asString(),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from leave_ledger where request_id=? and kind='CONSUME'",
                    Int::class.java,
                    id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from leave_requests where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }
}
