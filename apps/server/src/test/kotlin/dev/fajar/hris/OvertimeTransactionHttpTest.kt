package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class OvertimeTransactionHttpTest : OvertimeApiFixture() {
    @Test
    fun creationAndSubmissionFailuresRollBackHistoryApprovalJournalAndSync() {
        val f = overtimeFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val tables =
            listOf(
                "overtime_requests",
                "overtime_changes",
                "approval_requests",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )
        val before = tables.associateWith { rows(f, it) }
        overtimeProbe.beforeJournal = {
            if (it.action.startsWith("overtime."))
                throw DataIntegrityViolationException("Fixture rollback")
        }
        assertEquals(409, plan(f, id, key).statusCode())
        assertEquals(before, tables.associateWith { rows(f, it) })
        overtimeProbe.clear()
        overtimeProbe.omitHistory = true
        assertEquals(409, plan(f, id, key).statusCode())
        assertEquals(before, tables.associateWith { rows(f, it) })
        overtimeProbe.clear()
        overtimeBody(plan(f, id, key))
        val planned = tables.associateWith { rows(f, it) }
        val actualKey = UUID.randomUUID()
        overtimeProbe.omitHistory = true
        assertEquals(409, actual(f, id, actualKey).statusCode())
        assertEquals(planned, tables.associateWith { rows(f, it) })
        overtimeProbe.clear()
        overtimeProbe.beforeJournal = {
            if (it.action == "overtime.actual_submitted")
                throw DataIntegrityViolationException("Fixture rollback")
        }
        assertEquals(409, actual(f, id, actualKey).statusCode())
        assertEquals(planned, tables.associateWith { rows(f, it) })
        overtimeProbe.clear()
        overtimeBody(actual(f, id, actualKey))
        assertEquals(1, rows(f, "approval_requests"))
    }

    @Test
    fun decisionAndWithdrawalFailuresLeaveEveryAggregateAtItsPreviousVersion() {
        val f = overtimeFixture()
        val id = pending(f)
        val key = UUID.randomUUID()
        val tables =
            listOf(
                "overtime_changes",
                "approval_decisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )
        val before = tables.associateWith { rows(f, it) }
        overtimeProbe.beforeJournal = {
            if (it.action.startsWith("overtime."))
                throw DataIntegrityViolationException("Fixture rollback")
        }
        assertEquals(409, decide(f, id, key = key).statusCode())
        assertEquals(before, tables.associateWith { rows(f, it) })
        assertEquals(409, withdraw(f, id, 1).statusCode())
        assertEquals(before, tables.associateWith { rows(f, it) })
        overtimeProbe.clear()
        overtimeProbe.omitHistory = true
        assertEquals(409, decide(f, id, key = key).statusCode())
        assertEquals(before, tables.associateWith { rows(f, it) })
        overtimeProbe.clear()
        val view = details(f, id)
        assertEquals("PENDING", view["request"]["status"].asString())
        assertEquals("PENDING", view["approval"]["status"].asString())
        overtimeBody(decide(f, id, key = key))
        assertEquals(1, rows(f, "approval_decisions"))
    }

    @Test
    fun racingDecisionsAndWithdrawalsCommitExactlyOneTerminalTransition() {
        val f = overtimeFixture()
        val id = pending(f)
        Executors.newFixedThreadPool(2).use { pool ->
            val decision = pool.submit<java.net.http.HttpResponse<String>> { decide(f, id) }
            val withdrawal = pool.submit<java.net.http.HttpResponse<String>> { withdraw(f, id, 1) }
            assertEquals(
                listOf(200, 409),
                listOf(
                        decision.get(10, TimeUnit.SECONDS).statusCode(),
                        withdrawal.get(10, TimeUnit.SECONDS).statusCode(),
                    )
                    .sorted(),
            )
        }
        val view = details(f, id)
        assertTrue(view["request"]["status"].asString() in setOf("APPROVED", "WITHDRAWN"))
        assertEquals(2, view["request"]["version"].asLong())
        assertEquals(3, rows(f, "overtime_changes"))
        assertEquals(3, rows(f, "mobile_sync_changes"))
    }

    @Test
    fun databaseRejectsIncompleteDecisionsImmutableEvidenceAndForeignScope() {
        val f = overtimeFixture()
        val id = pending(f)
        val approval = UUID.fromString(details(f, id)["approval"]["id"].asString())
        for (statement in
            listOf(
                "delete from overtime_requests where company_id=?",
                "update overtime_requests set reason='rewrite' where company_id=?",
                "update overtime_requests set actual_break=1,version=version+1 where company_id=?",
                "delete from overtime_changes where company_id=?",
                "update overtime_changes set reason='rewrite' where company_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(statement, f.company)
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update approval_requests set status='APPROVED',version=version+1 where company_id=? and id=?",
                    f.company,
                    approval,
                )
        }
        assertEquals("PENDING", details(f, id)["request"]["status"].asString())
        val other = overtimeFixture()
        val visible =
            transactions.run(other.actor) {
                safeDatabaseCall {
                    runtimeJdbc.queryForObject(
                        "select count(*) from overtime_requests where company_id=?",
                        Int::class.java,
                        f.company,
                    )!!
                }
            }
        assertEquals(0, (visible as Result.Success).value)
        assertEquals(404, get(other.admin, "${other.path}/overtime/$id").statusCode())
    }
}
