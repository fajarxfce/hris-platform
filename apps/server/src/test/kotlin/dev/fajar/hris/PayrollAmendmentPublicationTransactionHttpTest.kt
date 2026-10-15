package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

class PayrollAmendmentPublicationTransactionHttpTest : PayrollAmendmentPublicationApiFixture() {
    @Test
    fun admissionRequiresItsJobPublicationRevisionAndJournalInOneCommit() {
        val scene = publicationScene()
        val before = publicationCounts(scene)
        for (omission in listOf("publication", "revision", "journal")) {
            when (omission) {
                "publication" -> publicationProbe.omitPublication = true
                "revision" -> draftProbe.omitRevision = true
                else -> payrollProbe.beforeJournal = {
                        if (it.action == "payroll.amendment_publication_started")
                            throw DataIntegrityViolationException(
                                "Owned publication admission fault"
                            )
                    }
            }
            val failed = admitPublication(scene)
            assertEquals(409, failed.statusCode(), "$omission: ${failed.body()}")
            publicationProbe.clear()
            draftProbe.clear()
            payrollProbe.clear()
            assertEquals(before, publicationCounts(scene))
            assertEquals(
                "APPROVED",
                draftView(scene.review.draft)["amendment"]["summary"]["status"].asString(),
            )
        }
        val committed = payrollBody(admitPublication(scene))
        assertEquals(committed, payrollBody(admitPublication(scene)))
    }

    @Test
    fun incompletePublicationNeverChangesEmployeeFactsPaymentsOrJobProgress() {
        val scene = publicationScene()
        val lease = publicationLease(scene)
        val before = publicationCounts(scene)
        val payslip = currentPayslip(scene)
        val progress = currentProgress(scene)
        for (omission in listOf("assessments", "heads", "revision", "journal")) {
            when (omission) {
                "assessments" -> publicationProbe.omitRevisions = true
                "heads" -> publicationProbe.omitHeads = true
                "revision" -> draftProbe.omitRevision = true
                else -> payrollProbe.beforeJournal = {
                        if (it.action == "payroll.amendment_published")
                            throw DataIntegrityViolationException("Owned publication outcome fault")
                    }
            }
            val failed = stepPublication(scene, lease)
            assertTrue(failed is Result.Failed, "$omission: $failed")
            publicationProbe.clear()
            draftProbe.clear()
            payrollProbe.clear()
            assertEquals(before, publicationCounts(scene))
            assertEquals(payslip, currentPayslip(scene))
            assertEquals(progress, currentProgress(scene))
            assertEquals("PENDING", publicationView(scene)[0]["publication"]["status"].asString())
            assertEquals(0, publicationView(scene)[0]["job"]["completedItems"].asInt())
        }
        assertEquals(Result.Success(JobStep(1, true)), stepPublication(scene, lease))
    }

    @Test
    fun competingRetriesAndWorkersProduceOnePublicationAndOneRevisedAssessment() {
        val scene = publicationScene()
        Executors.newFixedThreadPool(2).use { pool ->
            val admitted =
                (1..2)
                    .map { pool.submit<HttpResponse<String>> { admitPublication(scene) } }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(admitted[0], admitted[1])
            val lease = publicationLease(scene)
            val results =
                (1..2)
                    .map { pool.submit<Result<JobStep>> { stepPublication(scene, lease) } }
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success }, results.toString())
            assertEquals(
                1,
                results.count {
                    it == Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                },
            )
        }
        assertEquals(1, count(scene.source.payroll.company, "payroll_amendment_publications"))
        assertEquals(2, count(scene.source.payroll.company, "payroll_assessment_revisions"))
        assertEquals(1, currentPayslip(scene)["summary"]["version"].asLong())
    }

    @Test
    fun cancellationDuringPublicationRollsBackAndReleasesTheExecutorForRetry() {
        val scene = publicationScene()
        val lease = publicationLease(scene)
        val before = publicationCounts(scene)
        publicationProbe.afterHeads = {
            Thread.currentThread().interrupt()
            throw InterruptedException("Owned amendment publication interruption")
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<JobStep>> { stepPublication(scene, lease) }
            val failed =
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
            assertInstanceOf(InterruptedException::class.java, failed.cause)
            publicationProbe.clear()
            assertEquals(before, publicationCounts(scene))
            assertEquals(0, currentPayslip(scene)["summary"]["version"].asLong())
            val retried =
                pool
                    .submit<Result<JobStep>> {
                        assertFalse(Thread.currentThread().isInterrupted)
                        stepPublication(scene, lease)
                    }
                    .get(10, TimeUnit.SECONDS)
            assertEquals(Result.Success(JobStep(1, true)), retried)
        }
    }

    @Test
    fun lateLeaseExpiryRollsBackAllHeadsAndOldOwnersCannotFinishOrAbortAReplacement() {
        val scene = publicationScene()
        val first = publicationLease(scene)
        val before = publicationCounts(scene)
        publicationProbe.afterHeads = {
            runtimeJdbc.update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                first.job.request.id,
            )
        }
        assertTrue(stepPublication(scene, first) is Result.Failed)
        publicationProbe.clear()
        assertEquals(before, publicationCounts(scene))
        assertEquals(0, currentPayslip(scene)["summary"]["version"].asLong())
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                first.job.request.id,
            )
        val next =
            claimPayrollJobs(JobKind.PAYROLL_AMENDMENT_PUBLISH).single {
                it.job.request.id == first.job.request.id
            }
        assertNotEquals(first.token, next.token)
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost")),
            stepPublication(scene, first),
        )
        assertEquals(
            Result.Success(Unit),
            abortAmendment.execute(first, Failure(FailureKind.UNAVAILABLE, "fixture_failure")),
        )
        assertEquals(Result.Success(JobStep(1, true)), stepPublication(scene, next))
    }

    @Test
    fun anotherApprovedAmendmentCannotPublishAgainstReplacedSourceRevisions() {
        val first = publicationScene()
        val competing = publicationScene(first.source, "500000", first.review)
        val waiting = publicationLease(competing)
        publish(first)
        val prior = publicationCounts(competing)
        val result = stepPublication(competing, waiting)
        assertEquals("payroll_amendment_sources_changed", (result as Result.Failed).failure.code)
        assertEquals(prior, publicationCounts(competing))
        assertEquals(Result.Success(Unit), abortAmendment.execute(waiting, result.failure))
        assertEquals("APPROVED", publicationView(competing)[0]["amendment"]["status"].asString())
        assertEquals(1, currentPayslip(competing)["summary"]["version"].asLong())
        assertEquals(2, count(competing.source.payroll.company, "payroll_assessment_revisions"))
        val retry = competing.copy(id = UUID.randomUUID(), operation = UUID.randomUUID())
        payrollError(admitPublication(retry, 5), 409, "payroll_amendment_sources_changed")
    }
}
