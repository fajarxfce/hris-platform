package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.policies.availableJobActions
import dev.fajar.hris.payroll.domain.entities.*

/** Preparation hints do not reserve sources, permission or queue capacity. */
fun payrollCalculationIssues(
    period: PayrollPeriod,
    source: PayrollWorkPeriod?,
    policy: PayrollPolicy?,
    runCount: Int,
    pendingLeave: Boolean,
): List<Failure> = buildList {
    if (period.status != PayrollPeriodStatus.DRAFT || period.currentRunId != null)
        add(Failure(FailureKind.CONFLICT, "payroll_period_not_draft"))
    if (period.version > 250) add(Failure(FailureKind.CONFLICT, "payroll_period_revision_limit"))
    if (source == null || !source.closed || source.jobId == null)
        add(Failure(FailureKind.CONFLICT, "payroll_workforce_not_closed"))
    else if (source.timezone != period.timezone)
        add(Failure(FailureKind.CONFLICT, "payroll_timezone_review_required"))
    val effective = requireEffectivePayrollPolicy(policy, period.earningsMonth)
    if (effective is Result.Failed) add(effective.failure)
    if (runCount >= 20) add(Failure(FailureKind.CONFLICT, "payroll_run_limit"))
    if (pendingLeave) add(Failure(FailureKind.CONFLICT, "payroll_leave_decision_pending"))
}

fun availablePayrollCalculationActions(
    actor: Actor,
    run: PayrollRun,
    period: PayrollPeriod,
    job: BackgroundJob,
    attempts: Int,
    review: PayrollReview?,
): Set<PayrollCalculationAction> = buildSet {
    if ("cancel" in availableJobActions(actor, job)) add(PayrollCalculationAction.CANCEL_JOB)
    if ("payroll.calculate" !in actor.permissions || period.currentRunId != run.id) return@buildSet
    if (
        run.actorId == actor.accountId &&
            run.status in setOf(PayrollRunStatus.PROCESSING, PayrollRunStatus.STOPPED) &&
            job.status in setOf(JobStatus.FAILED, JobStatus.CANCELLED) &&
            period.status == PayrollPeriodStatus.PROCESSING &&
            attempts < 8 &&
            run.version < 23
    )
        add(PayrollCalculationAction.RESUME)
    if (
        run.status !in setOf(PayrollRunStatus.ABANDONED, PayrollRunStatus.FINALIZED) &&
            job.status !in setOf(JobStatus.QUEUED, JobStatus.RUNNING) &&
            run.version <= 24 &&
            period.version <= 254 &&
            review?.status !in setOf(PayrollReviewStatus.PENDING, PayrollReviewStatus.APPROVED)
    )
        add(PayrollCalculationAction.ABANDON)
}
