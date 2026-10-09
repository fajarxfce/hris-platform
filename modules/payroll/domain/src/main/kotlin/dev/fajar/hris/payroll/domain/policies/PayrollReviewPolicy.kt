package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*

fun validatePayrollReviewSubmission(
    run: PayrollRun,
    latest: PayrollReview?,
    totals: PayrollRunTotals,
): Result<Unit> =
    when {
        run.status != PayrollRunStatus.CALCULATED ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_calculation_required"))
        run.processed != run.totalEmployees ||
            run.failed != 0 ||
            totals.employeeCount != run.totalEmployees ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_calculation_has_failures"))
        latest?.status in setOf(PayrollReviewStatus.PENDING, PayrollReviewStatus.APPROVED) ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_review_active"))
        latest?.status == PayrollReviewStatus.REJECTED ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_recalculation_required"))
        (latest?.number ?: 0) >= 8 ->
            Result.Failed(
                Failure(
                    FailureKind.CONFLICT,
                    "payroll_review_capacity",
                    parameters = mapOf("maximum" to "8"),
                )
            )
        else -> Result.Success(Unit)
    }

fun payrollReviewStatus(status: ApprovalStatus): PayrollReviewStatus =
    when (status) {
        ApprovalStatus.PENDING,
        ApprovalStatus.BLOCKED -> PayrollReviewStatus.PENDING
        ApprovalStatus.APPROVED -> PayrollReviewStatus.APPROVED
        ApprovalStatus.REJECTED -> PayrollReviewStatus.REJECTED
        ApprovalStatus.CANCELLED -> PayrollReviewStatus.WITHDRAWN
    }
