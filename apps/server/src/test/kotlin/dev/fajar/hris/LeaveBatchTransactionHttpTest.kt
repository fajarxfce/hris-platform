package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class LeaveBatchTransactionHttpTest : LeaveBatchApiFixture() {
    @Test
    fun missingResultsAndLateFailuresRollBackGrantCheckpointAndEvidenceTogether() {
        val f = accountingFixture()
        val lease = beginBatch(f)
        val id = batchId(lease)
        val tables =
            listOf(
                "leave_accounts",
                "leave_ledger",
                "leave_accrual_postings",
                "leave_accrual_years",
                "leave_batch_results",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )
        val before = tables.associateWith { accountingRows(f, it) }
        batchProbe.omitResult = true
        assertTrue(stepBatch(f, lease) is Result.Failed)
        assertEquals(before, tables.associateWith { accountingRows(f, it) })
        assertEquals(0, batchView(f, id)["job"]["completedItems"].asInt())
        batchProbe.clear()
        batchProbe.afterResult = {
            throw DataIntegrityViolationException("Fixture after-result failure")
        }
        assertTrue(stepBatch(f, lease) is Result.Failed)
        assertEquals(before, tables.associateWith { accountingRows(f, it) })
        assertEquals(0, batchView(f, id)["job"]["completedItems"].asInt())
        batchProbe.clear()
        accountingProbe.omittedKind = "GRANT"
        assertTrue(stepBatch(f, lease) is Result.Failed)
        assertEquals(before, tables.associateWith { accountingRows(f, it) })
        accountingProbe.clear()
        drainBatch(f, lease)
        assertEquals(2, batchView(f, id)["counts"]["applied"].asInt())
    }

    @Test
    fun finalAuditFailureRetainsFinishedEmployeesButDoesNotCompleteTheJob() {
        val f = accountingFixture()
        val lease = beginBatch(f)
        val id = batchId(lease)
        repeat(2) { assertTrue(stepBatch(f, lease) is Result.Success) }
        accountingProbe.beforeJournal = {
            if (it.action == "leave.batch_completed")
                throw DataIntegrityViolationException("Fixture final audit failure")
        }
        assertTrue(stepBatch(f, lease) is Result.Failed)
        val waiting = batchView(f, id)
        assertEquals(2, waiting["counts"]["applied"].asInt())
        assertEquals(2, waiting["job"]["completedItems"].asInt())
        assertEquals("RUNNING", waiting["batch"]["status"].asString())
        assertEquals("RUNNING", waiting["job"]["status"].asString())
        accountingProbe.clear()
        accountingBody(accountingPolicy(f, version = 1, days = "2"))
        assertEquals(Result.Success(JobStep(3, true)), stepBatch(f, lease))
        assertEquals(2, accountingRows(f, "leave_accrual_postings"))
    }

    @Test
    fun startAndResumeFailuresLeaveNoOrphanJobsTargetsOrReceipts() {
        val f = accountingFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val payload = batchBody(f, id)
        val tables =
            listOf(
                "leave_batches",
                "leave_batch_targets",
                "leave_batch_attempts",
                "background_jobs",
                "audit_entries",
                "outbox_events",
                "operation_receipts",
            )
        val before = tables.associateWith { accountingRows(f, it) }
        accountingProbe.beforeJournal = {
            if (it.action == "leave.batch_started")
                throw DataIntegrityViolationException("Fixture batch start failure")
        }
        assertEquals(409, createBatch(f, payload, key).statusCode())
        assertEquals(before, tables.associateWith { accountingRows(f, it) })
        accountingProbe.clear()
        accountingBody(createBatch(f, payload, key))
        val lease = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == id }
        cancelBatch(f, lease)
        val stopped = tables.associateWith { accountingRows(f, it) }
        val resumeKey = UUID.randomUUID()
        accountingProbe.beforeJournal = {
            if (it.action == "leave.batch_resumed")
                throw DataIntegrityViolationException("Fixture resume failure")
        }
        assertEquals(409, resumeBatch(f, id, 1, resumeKey).statusCode())
        assertEquals(stopped, tables.associateWith { accountingRows(f, it) })
        assertEquals("STOPPED", batchView(f, id)["batch"]["status"].asString())
        accountingProbe.clear()
        accountingBody(resumeBatch(f, id, 1, resumeKey))
        val resumed = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == id }
        drainBatch(f, resumed)
    }

    @Test
    fun interruptionAfterAResultPropagatesAndRollsBackThePendingStep() {
        val f = accountingFixture()
        val lease = beginBatch(f)
        val actor = accountingActor(f)
        batchProbe.afterResult = {
            Thread.currentThread().interrupt()
            throw InterruptedException("Fixture pending step cancellation")
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<JobStep>> { stepBatch(f, lease, actor) }
            val error =
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
            assertTrue(error.cause is InterruptedException)
        }
        for (table in
            listOf(
                "leave_accounts",
                "leave_ledger",
                "leave_accrual_postings",
                "leave_batch_results",
            )) assertEquals(0, accountingRows(f, table))
        assertEquals(0, batchView(f, batchId(lease))["job"]["completedItems"].asInt())
        batchProbe.clear()
        drainBatch(f, lease)
    }

    @Test
    fun retainedBatchEvidenceIsImmutableAndHiddenByAnotherCompanyRlsContext() {
        val f = accountingFixture()
        val lease = beginBatch(f)
        val id = batchId(lease)
        drainBatch(f, lease)
        val other = accountingFixture()
        for (table in
            listOf(
                "leave_batches",
                "leave_batch_targets",
                "leave_batch_attempts",
                "leave_batch_results",
            )) {
            assertThrows(DataAccessException::class.java) {
                database().update("delete from $table where company_id=?", f.company)
            }
            val count =
                transactions.run(accountingActor(other)) {
                    safeDatabaseCall {
                        runtimeJdbc.queryForObject(
                            "select count(*) from $table where company_id=?",
                            Int::class.java,
                            f.company,
                        )
                    }
                }
            assertEquals(Result.Success(0), count)
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_batch_results set failure_code='rewrite' where company_id=?",
                    f.company,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_batches set reason='rewrite',version=version+1 where company_id=? and id=?",
                    f.company,
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_batch_attempts set base_completed=base_completed+1 where company_id=?",
                    f.company,
                )
        }
    }
}
