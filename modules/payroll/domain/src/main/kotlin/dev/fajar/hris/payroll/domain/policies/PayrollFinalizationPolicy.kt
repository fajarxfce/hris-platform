package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*

fun requireApprovedPayroll(
    run: PayrollRun,
    review: PayrollReview?,
    approval: ApprovalRequest?,
): Result<Unit> =
    when {
        run.status == PayrollRunStatus.FINALIZED ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_already_finalized"))
        run.status != PayrollRunStatus.CALCULATED ||
            run.succeeded != run.totalEmployees ||
            run.failed != 0 ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_successful_calculation_required"))
        review == null ||
            approval == null ||
            review.runId != run.id ||
            review.runVersion != run.version ||
            review.status != PayrollReviewStatus.APPROVED ||
            approval.id != review.approvalId ||
            approval.status != ApprovalStatus.APPROVED ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_approved_review_required"))
        else -> Result.Success(Unit)
    }

fun requirePayrollPublicationSources(readiness: PayrollPublicationReadiness): Result<Unit> =
    when {
        readiness.changedEmployments > 0 ->
            Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
        readiness.duplicatePeople > 0 ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_person_duplicated"))
        readiness.assessedHolidays > 0 ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_holiday_already_assessed"))
        readiness.staleTaxHistories > 0 ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_history_changed"))
        else -> Result.Success(Unit)
    }

fun canReadPayrollFinalization(actor: Actor): Boolean =
    canReadPayrollInput(actor) || "payroll.finalize" in actor.permissions
