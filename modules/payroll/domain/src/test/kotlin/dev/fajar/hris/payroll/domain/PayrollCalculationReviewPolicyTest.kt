package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollCalculationReviewPolicyTest : MonthlyPayrollFixture() {
    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private val company = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val runId = UUID.randomUUID()
    private val jobId = UUID.randomUUID()
    private val actor = Actor(author, company, setOf("payroll.calculate"), now, UUID.randomUUID())
    private val period =
        PayrollPeriod(
            UUID.randomUUID(),
            YearMonth.of(2026, 9),
            LocalDate.of(2026, 10, 1),
            "Asia/Jakarta",
            2,
            author,
            now,
            PayrollPeriodStatus.DRAFT,
            0,
        )
    private val source =
        PayrollWorkPeriod(
            UUID.randomUUID(),
            period.earningsMonth,
            3,
            true,
            UUID.randomUUID(),
            period.timezone,
        )
    private val policy = facts().policy
    private val run =
        PayrollRun(
            runId,
            period.id,
            1,
            period.earningsMonth,
            LocalDate.of(2026, 9, 30),
            period.plannedPaymentDate,
            period.timezone,
            requireNotNull(source.jobId),
            source.version,
            0,
            2,
            author,
            "Reviewed liability",
            "Calculate September",
            now,
            jobId,
            PayrollRunStatus.STOPPED,
            1,
        )
    private val processing =
        period.copy(status = PayrollPeriodStatus.PROCESSING, version = 1, currentRunId = runId)
    private val job =
        BackgroundJob(
            JobRequest(
                jobId,
                company,
                author,
                JobKind.PAYROLL_CALCULATE,
                UUID.randomUUID(),
                mapOf("runId" to runId.toString()),
                now,
                0,
                UUID.randomUUID(),
                now,
                3,
            ),
            JobStatus.CANCELLED,
            1,
            true,
            1,
            emptyMap(),
            null,
            now,
            2,
        )

    @Test
    fun preparationKeepsMissingEvidenceAndBoundedCapacityExplicit() {
        assertTrue(payrollCalculationIssues(period, source, policy, 19, false).isEmpty())
        val missing = payrollCalculationIssues(period.copy(version = 251), null, null, 20, true)
        assertEquals(
            setOf(
                "payroll_period_revision_limit",
                "payroll_workforce_not_closed",
                "payroll_policy_not_found",
                "payroll_run_limit",
                "payroll_leave_decision_pending",
            ),
            missing.map { it.code }.toSet(),
        )
        assertEquals(
            FailureKind.NOT_FOUND,
            missing.single { it.code == "payroll_policy_not_found" }.kind,
        )
        assertEquals(
            listOf("payroll_period_not_draft"),
            payrollCalculationIssues(processing, source, policy, 1, false).map { it.code },
        )
        assertEquals(
            listOf("payroll_period_not_draft"),
            payrollCalculationIssues(period.copy(currentRunId = runId), source, policy, 1, false)
                .map { it.code },
        )
    }

    @Test
    fun workforceClosureTimezoneAndPolicyCoverageAreIndependentRequirements() {
        for (unclosed in listOf(source.copy(closed = false), source.copy(jobId = null))) {
            assertEquals(
                listOf("payroll_workforce_not_closed"),
                payrollCalculationIssues(period, unclosed, policy, 0, false).map { it.code },
            )
        }
        val issues =
            payrollCalculationIssues(
                period,
                source.copy(timezone = "UTC"),
                policy.copy(effectiveUntil = YearMonth.of(2026, 8)),
                0,
                false,
            )
        assertEquals(
            setOf("payroll_timezone_review_required", "payroll_policy_not_effective"),
            issues.map { it.code }.toSet(),
        )
        assertEquals(
            mapOf("validFrom" to "2026-01", "validUntil" to "2026-08"),
            issues.single { it.code == "payroll_policy_not_effective" }.parameters,
        )
    }

    @Test
    fun resumeBelongsToTheOriginalAuthorAndCannotRewindCompletedResults() {
        assertEquals(
            setOf(PayrollCalculationAction.RESUME, PayrollCalculationAction.ABANDON),
            actions(),
        )
        assertEquals(
            setOf(PayrollCalculationAction.ABANDON),
            actions(actor = actor.copy(accountId = UUID.randomUUID())),
        )
        assertEquals(
            emptySet<PayrollCalculationAction>(),
            actions(actor = actor.copy(permissions = setOf("payroll.review"))),
        )
        assertEquals(setOf(PayrollCalculationAction.ABANDON), actions(attempts = 8))
        assertEquals(setOf(PayrollCalculationAction.ABANDON), actions(run = run.copy(version = 23)))
        assertEquals(
            setOf(PayrollCalculationAction.ABANDON),
            actions(
                run = run.copy(status = PayrollRunStatus.CALCULATED),
                job = job.copy(status = JobStatus.SUCCEEDED),
            ),
        )
        assertEquals(
            setOf(PayrollCalculationAction.RESUME, PayrollCalculationAction.ABANDON),
            actions(
                run = run.copy(status = PayrollRunStatus.PROCESSING),
                job = job.copy(status = JobStatus.FAILED),
            ),
        )
    }

    @Test
    fun cancellationUsesJobOwnershipWithoutGrantingCalculationAuthority() {
        val running =
            job.copy(status = JobStatus.RUNNING, cancellationRequested = false, finishedAt = null)
        assertEquals(setOf(PayrollCalculationAction.CANCEL_JOB), actions(job = running))
        assertEquals(
            setOf(PayrollCalculationAction.CANCEL_JOB),
            actions(
                actor =
                    actor.copy(
                        accountId = UUID.randomUUID(),
                        permissions = setOf("payroll.review", "jobs.manage"),
                    ),
                job = running,
            ),
        )
        assertEquals(
            emptySet<PayrollCalculationAction>(),
            actions(
                actor =
                    actor.copy(
                        accountId = UUID.randomUUID(),
                        permissions = setOf("payroll.review"),
                    ),
                job = running,
            ),
        )
        assertEquals(
            emptySet<PayrollCalculationAction>(),
            actions(job = running.copy(cancellationRequested = true)),
        )
    }

    @Test
    fun abandonmentRespectsReviewedFinalAndObsoleteEvidence() {
        val calculated = run.copy(status = PayrollRunStatus.CALCULATED)
        val review =
            PayrollReview(
                UUID.randomUUID(),
                runId,
                1,
                1,
                UUID.randomUUID(),
                period.earningsMonth.atEndOfMonth(),
                PayrollRunTotals(2, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN),
                author,
                now,
                "Approve results",
                PayrollReviewStatus.PENDING,
                0,
            )
        for (state in listOf(PayrollReviewStatus.PENDING, PayrollReviewStatus.APPROVED)) {
            assertEquals(
                emptySet<PayrollCalculationAction>(),
                actions(run = calculated, review = review.copy(status = state)),
            )
        }
        for (state in listOf(PayrollReviewStatus.REJECTED, PayrollReviewStatus.WITHDRAWN)) {
            assertEquals(
                setOf(PayrollCalculationAction.ABANDON),
                actions(run = calculated, review = review.copy(status = state)),
            )
        }
        for (state in listOf(PayrollRunStatus.FINALIZED, PayrollRunStatus.ABANDONED)) {
            assertEquals(
                emptySet<PayrollCalculationAction>(),
                actions(run = run.copy(status = state)),
            )
        }
        assertEquals(
            emptySet<PayrollCalculationAction>(),
            actions(period = processing.copy(currentRunId = UUID.randomUUID())),
        )
        assertEquals(
            emptySet<PayrollCalculationAction>(),
            actions(run = calculated.copy(version = 25)),
        )
        assertEquals(
            emptySet<PayrollCalculationAction>(),
            actions(run = calculated, period = processing.copy(version = 255)),
        )
    }

    private fun actions(
        actor: Actor = this.actor,
        run: PayrollRun = this.run,
        period: PayrollPeriod = processing,
        job: BackgroundJob = this.job,
        attempts: Int = 1,
        review: PayrollReview? = null,
    ) = availablePayrollCalculationActions(actor, run, period, job, attempts, review)
}
