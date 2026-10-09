package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

class PayrollFinalizationTransactionHttpTest : PayrollFinalizationApiFixture() {
    @Test
    fun startAndPublicationRollbackEveryEffectAndTheSameOperationCanRetry() {
        val f = approved()
        val company = f.calculation.people.payroll.company
        val tables =
            listOf(
                "payroll_finalizations",
                "payroll_assessments",
                "background_jobs",
                "payroll_period_changes",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
        val before = tables.associateWith { count(company, it) }
        val body = finalizationBody(f)
        val key = UUID.randomUUID()
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.finalization_started")
                throw DataIntegrityViolationException("Owned start failure")
        }
        assertEquals(409, startFinalization(f, body, key).statusCode())
        payrollProbe.clear()
        assertEquals(before, tables.associateWith { count(company, it) })
        val id = UUID.fromString(payrollBody(startFinalization(f, body, key))["id"].asString())
        val lease = claimPayrollJobs(JobKind.PAYROLL_FINALIZE).single()
        for (omit in listOf(true, false)) {
            val prior = tables.associateWith { count(company, it) }
            val run = runView(f.calculation, f.run)
            finalizationProbe.omitAssessments = omit
            if (!omit)
                payrollProbe.beforeJournal = {
                    if (it.action == "payroll.finalized")
                        throw DataIntegrityViolationException("Owned publication failure")
                }
            assertTrue(stepFinalization(f, lease) is Result.Failed)
            finalizationProbe.clear()
            payrollProbe.clear()
            assertEquals(prior, tables.associateWith { count(company, it) })
            assertEquals(run, runView(f.calculation, f.run))
            assertTrue(finalizationView(f, id)["finalization"]["publishedAt"].isNull)
            assertEquals(0, finalizationView(f, id)["job"]["completedItems"].asInt())
        }
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(f, lease))
    }

    @Test
    fun competingCommandsAndWorkersCannotPublishTwice() {
        val f = approved()
        val body = finalizationBody(f)
        val key = UUID.randomUUID()
        Executors.newFixedThreadPool(2).use { pool ->
            val replies =
                (1..2)
                    .map {
                        pool.submit<java.net.http.HttpResponse<String>> {
                            startFinalization(f, body, key)
                        }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(replies[0], replies[1])
        }
        val lease = claimPayrollJobs(JobKind.PAYROLL_FINALIZE).single()
        Executors.newFixedThreadPool(2).use { pool ->
            val replies =
                (1..2)
                    .map { pool.submit<Result<JobStep>> { stepFinalization(f, lease) } }
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, replies.count { it is Result.Success })
            assertEquals(
                1,
                replies.count {
                    it == Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                },
            )
        }
        assertEquals(1, count(f.calculation.people.payroll.company, "payroll_assessments"))
        assertEquals(1, count(f.calculation.people.payroll.company, "payroll_finalizations"))
    }

    @Test
    fun interruptionAfterReferenceInsertionRollsBackAndReleasesTheWorkerThread() {
        val f = approved()
        val lease = beginFinalization(f)
        finalizationProbe.afterAssessments = {
            Thread.currentThread().interrupt()
            throw InterruptedException("Owned publication interruption")
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<JobStep>> { stepFinalization(f, lease) }
            val error =
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
            assertInstanceOf(InterruptedException::class.java, error.cause)
            assertFalse(
                pool
                    .submit<Boolean> { Thread.currentThread().isInterrupted }
                    .get(5, TimeUnit.SECONDS)
            )
        }
        finalizationProbe.clear()
        assertEquals(0, count(f.calculation.people.payroll.company, "payroll_assessments"))
        assertTrue(finalizationView(f, finalizationId(lease))["finalization"]["publishedAt"].isNull)
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(f, lease))
    }

    @Test
    fun anExpiredLeaseCannotPublishAndItsReplacementCanFinish() {
        val f = approved()
        val first = beginFinalization(f)
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where company_id=? and id=?",
                f.calculation.people.payroll.company,
                first.job.request.id,
            )
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost")),
            stepFinalization(f, first),
        )
        val next = claimPayrollJobs(JobKind.PAYROLL_FINALIZE).single()
        assertNotEquals(first.token, next.token)
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(f, next))
        assertEquals(
            Result.Success(Unit),
            abortFinalization.execute(first, Failure(FailureKind.UNAVAILABLE, "fixture_failure")),
        )
        assertEquals(
            "SUCCEEDED",
            finalizationView(f, finalizationId(next))["job"]["status"].asString(),
        )
    }

    @Test
    fun leaseExpiryAfterAcquiringReferencesCannotPublishLateEffects() {
        val f = approved()
        val lease = beginFinalization(f)
        finalizationProbe.afterAssessments = {
            runtimeJdbc.update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        }
        val result = stepFinalization(f, lease)
        assertTrue(result is Result.Failed, result.toString())
        finalizationProbe.clear()
        assertEquals(0, count(f.calculation.people.payroll.company, "payroll_assessments"))
        assertTrue(finalizationView(f, finalizationId(lease))["finalization"]["publishedAt"].isNull)
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(f, lease))
    }
}
