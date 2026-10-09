package dev.fajar.hris

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class LeaveLedgerHttpTest : LeaveApiFixture() {
    @Test
    fun policiesKeepEffectiveHistoryAndStableCodes() {
        val f = leaveFixture()
        val changed = policy(f, 0, "2026-11-01", false)
        assertEquals(200, changed.statusCode(), changed.body())
        val past = get(f.worker, "/api/v1/companies/${f.company}/leave/types?asOf=2026-10-01")
        assertEquals(200, past.statusCode(), past.body())
        assertTrue(json.readTree(past.body()).get("items")[0].get("paid").asBoolean())
        assertEquals(0, json.readTree(past.body()).get("items")[0].get("appliedRevision").asLong())
        assertEquals(1, json.readTree(past.body()).get("items")[0].get("version").asLong())
        val future = get(f.worker, "/api/v1/companies/${f.company}/leave/types?asOf=2026-11-01")
        assertFalse(json.readTree(future.body()).get("items")[0].get("paid").asBoolean())
        assertEquals(409, policy(f, 0).statusCode())
        val renamed = policy(f, 1, code = "RENAMED")
        assertEquals(422, renamed.statusCode(), renamed.body())
        assertEquals(
            "leave_type_code_immutable",
            json.readTree(renamed.body()).get("code").asString(),
        )
        assertThrows(DataAccessException::class.java) {
            database().update("delete from leave_type_revisions where type_id=?", f.type)
        }
    }

    @Test
    fun concurrentReductionsCannotOverspendAndLedgerReplaysDoNotDoubleGrant() {
        val f = leaveFixture()
        val key = UUID.randomUUID()
        val granted = adjust(f, "2.5", key, expectedVersion = 0)
        assertEquals(200, granted.statusCode(), granted.body())
        clock.set(Instant.parse("2026-10-01T16:00:00Z"))
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        adjust(f, "-2").statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(listOf(200, 409), results.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals("0.5", balance(f).get("availableDays").asString())
        assertEquals(granted.body(), adjust(f, "2.5", key, expectedVersion = 0).body())
        assertEquals(409, adjust(f, "3", key).statusCode())
        val page = ledger(f, suffix = "?limit=1")
        assertEquals(200, page.statusCode(), page.body())
        val rows = json.readTree(page.body()).get("entries")
        assertEquals("-2", rows.get("items")[0].get("availableDeltaDays").asString())
        val after = rows.get("nextCursor").asString()
        val next = ledger(f, suffix = "?after=$after&limit=1")
        assertEquals(
            "2.5",
            json
                .readTree(next.body())
                .get("entries")
                .get("items")[0]
                .get("availableDeltaDays")
                .asString(),
        )
        assertEquals(422, adjust(f, "0.25").statusCode())
        assertEquals(422, adjust(f, "1e6").statusCode())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from leave_ledger where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update("update leave_ledger set available_delta=100 where company_id=?", f.company)
        }
    }

    @Test
    fun currentTeamScopeAndIndependentAdjustmentsApplyToLeaveBalances() {
        val f = leaveFixture()
        assertEquals(200, adjust(f, "5").statusCode())
        assertEquals(200, ledger(f, f.supervisor).statusCode())
        val moved =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees/${f.employee}/revisions",
                json.writeValueAsString(
                    mapOf(
                        "version" to 0,
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2026-09-01",
                                "startDate" to "2025-01-01",
                                "status" to "ACTIVE",
                                "contract" to "PERMANENT",
                            ),
                        "reason" to "Reporting transfer",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, moved.statusCode(), moved.body())
        assertEquals(404, ledger(f, f.supervisor).statusCode())
        assertEquals(200, ledger(f, f.worker).statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.manage')",
                f.company,
                f.account,
            )
        val own =
            command(
                f.worker,
                "/api/v1/companies/${f.company}/leave/employees/${f.employee}/balances/${f.type}/2026/adjustments",
                """{"days":"1","reason":"Own grant","expectedVersion":1}""",
                f.workerCsrf,
                UUID.randomUUID(),
            )
        assertEquals(403, own.statusCode(), own.body())
        assertEquals("self_adjustment_denied", json.readTree(own.body()).get("code").asString())
        val unknown =
            get(
                f.worker,
                "/api/v1/companies/${f.company}/leave/employees/${f.employee}/balances/${UUID.randomUUID()}/2026",
            )
        assertEquals(404, unknown.statusCode(), unknown.body())
        assertEquals(200, policy(f, 0, active = false).statusCode())
        assertEquals(200, adjust(f, "-1").statusCode())
        assertEquals("4", balance(f).get("availableDays").asString())
    }
}
