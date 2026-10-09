package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollRunRecoveryHttpTest : PayrollRunApiFixture() {
    @Test
    fun explicitRecoveryKeepsCommittedResultsAndProcessesOnlyTheRemainingTargets() {
        val f = calculationFixture(extraEmployees = 2)
        val first = beginRun(f)
        val id = runId(first)
        assertEquals(Result.Success(JobStep(1, false)), stepRun(f, first))
        val retained = runView(f, id)["results"]["items"][0]
        stopRun(f, first)
        assertEquals("STOPPED", runView(f, id)["run"]["status"].asString())
        val other =
            payrollMember(f.people.payroll.company, setOf("company.read", "payroll.calculate"))
        payrollError(resumeRun(f, id, member = other), 403, "payroll_run_author_required")
        val key = UUID.randomUUID()
        val resumed = payrollBody(resumeRun(f, id, key = key))
        assertEquals(resumed, payrollBody(resumeRun(f, id, key = key)))
        val next = claimPayrollJobs(JobKind.PAYROLL_CALCULATE).single()
        assertNotEquals(first.job.request.id, next.job.request.id)
        assertEquals(3, next.job.request.totalItems)
        assertTrue(stepRun(f, first) is Result.Failed)
        drainRun(f, next)
        val view = runView(f, id)
        assertEquals(3, view["run"]["processed"].asInt())
        assertEquals(2, view["attempts"].size())
        assertEquals(1, view["attempts"][1]["baseCompleted"].asInt())
        assertEquals(retained, view["results"]["items"][0])
        assertEquals(3, count(f.people.payroll.company, "payroll_run_results"))
    }

    @Test
    fun anExpiredLeaseAndExhaustedProcessCanRecoverWithoutRewindingTheRun() {
        val f = calculationFixture(extraEmployees = 1)
        val first = beginRun(f)
        assertEquals(Result.Success(JobStep(1, false)), stepRun(f, first))
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                first.job.request.id,
            )
        val second = claimPayrollJobs(JobKind.PAYROLL_CALCULATE).single()
        assertNotEquals(first.token, second.token)
        assertTrue(stepRun(f, first) is Result.Failed)
        assertEquals(1, second.job.completedItems)
        database()
            .update(
                "update background_jobs set attempts=8,lease_until=clock_timestamp()-interval '1 second' where id=?",
                second.job.request.id,
            )
        assertTrue(claimPayrollJobs(JobKind.PAYROLL_CALCULATE).isEmpty())
        val view = runView(f, runId(first))
        assertEquals("FAILED", view["job"]["status"].asString())
        assertEquals("PROCESSING", view["run"]["status"].asString())
        payrollBody(resumeRun(f, runId(first), 0))
        drainRun(f, claimPayrollJobs(JobKind.PAYROLL_CALCULATE).single())
        assertEquals(2, count(f.people.payroll.company, "payroll_run_results"))
    }

    @Test
    fun explicitAttemptsAreFiniteAndAbandonmentStillRemainsAvailable() {
        val f = calculationFixture(prepare = false)
        var lease = beginRun(f)
        val id = runId(lease)
        repeat(8) { attempt ->
            stopRun(f, lease)
            if (attempt < 7) {
                payrollBody(resumeRun(f, id, (attempt * 2 + 1).toLong()))
                lease = claimPayrollJobs(JobKind.PAYROLL_CALCULATE).single()
            }
        }
        payrollError(resumeRun(f, id, 15), 409, "payroll_run_attempt_limit")
        assertEquals(8, count(f.people.payroll.company, "payroll_run_attempts"))
        payrollBody(abandonRun(f, id, 15, 1))
        assertEquals("ABANDONED", runView(f, id)["run"]["status"].asString())
    }

    @Test
    fun abandoningACalculationPreservesItsEvidenceAndReopensItsPeriodForCorrections() {
        val f = calculationFixture()
        val lease = beginRun(f)
        val id = runId(lease)
        payrollError(abandonRun(f, id, 0, 1), 409, "payroll_job_not_stopped")
        drainRun(f, lease)
        val first = runView(f, id)["results"]["items"][0]
        payrollError(abandonRun(f, id, periodVersion = 1), 409, "stale_version")
        val key = UUID.randomUUID()
        val abandoned = payrollBody(abandonRun(f, id, key = key))
        assertEquals(abandoned, payrollBody(abandonRun(f, id, key = key)))
        assertEquals(first, runView(f, id)["results"]["items"][0])
        payrollBody(inputSave(f.people, f.work, inputBody(f.work, 1)))
        payrollBody(inputVerify(f.people, 2))
        val nextId = UUID.randomUUID()
        payrollBody(createRun(f, runBody(f, nextId, mapOf("expectedPeriodVersion" to 3))))
        drainRun(f, claimPayrollJobs(JobKind.PAYROLL_CALCULATE).single())
        val next = runView(f, nextId)
        assertEquals(1, next["run"]["failed"].asInt())
        assertEquals(
            "payroll_day_review_required",
            next["results"]["items"][0]["failure"]["code"].asString(),
        )
        assertEquals(
            "2026-09-01",
            next["results"]["items"][0]["failure"]["parameters"]["workDate"].asString(),
        )
        assertEquals(2, count(f.people.payroll.company, "payroll_run_results"))
    }
}
