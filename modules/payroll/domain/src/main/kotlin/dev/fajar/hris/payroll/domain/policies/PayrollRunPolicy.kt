package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.*

fun validatePayrollRunStart(
    incomeDueDate: LocalDate,
    reference: String,
    reason: String,
    periodVersion: Long,
    workVersion: Long,
    policyVersion: Long,
): Result<Unit> =
    when {
        incomeDueDate.year !in 2024..2100 ||
            reference.isBlank() ||
            reference.length > 200 ||
            reference.any(Char::isISOControl) ||
            reason.isBlank() ||
            reason.length > 1000 ->
            Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_run"))
        periodVersion !in 0..254 || workVersion < 0 || policyVersion !in 0..999 ->
            Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        else -> Result.Success(Unit)
    }

fun validatePayrollRunJob(actor: Actor, lease: JobLease): Result<Unit> {
    val request = lease.job.request
    if (
        actor.accountId != request.actorId ||
            actor.companyId != request.companyId ||
            request.kind != JobKind.PAYROLL_CALCULATE
    )
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
    return actor.requirePermission("payroll.calculate")
}

fun validatePayrollRunProgress(
    run: PayrollRun,
    job: BackgroundJob,
    attempt: PayrollRunAttempt,
): Result<Unit> =
    if (
        run.processed != attempt.baseCompleted + job.completedItems ||
            run.processed != run.succeeded + run.failed ||
            job.request.totalItems != run.totalEmployees - attempt.baseCompleted + 1 ||
            job.request.id != run.jobId ||
            attempt.jobId != run.jobId ||
            run.processed !in 0..run.totalEmployees
    )
        Result.Failed(Failure(FailureKind.CONFLICT, "payroll_run_checkpoint_inconsistent"))
    else Result.Success(Unit)

fun requirePayrollSources(
    target: PayrollRunTarget,
    input: PayrollInput?,
    opening: PayrollTaxOpening?,
    run: PayrollRun,
    employmentVersion: Long?,
): Result<Unit> =
    when {
        employmentVersion != target.employmentVersion ->
            Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
        target.compensationRevision == null ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_compensation_required"))
        input == null -> Result.Failed(Failure(FailureKind.CONFLICT, "payroll_input_required"))
        input.status != PayrollInputStatus.VERIFIED ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_input_verification_required"))
        input.employmentVersion != target.employmentVersion ->
            Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
        input.workJobId != run.workJobId || input.workPeriodVersion != run.workPeriodVersion ->
            Result.Failed(Failure(FailureKind.CONFLICT, "stale_workforce_version"))
        opening == null ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_opening_required"))
        opening.status != PayrollTaxOpeningStatus.VERIFIED ->
            Result.Failed(
                Failure(FailureKind.CONFLICT, "payroll_tax_opening_verification_required")
            )
        else -> Result.Success(Unit)
    }
