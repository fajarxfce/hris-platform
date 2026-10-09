package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollRunTotals.toResponse() =
    PayrollRunTotalsResponse(
        employeeCount,
        taxableGross.toPlainString(),
        withheld.toPlainString(),
        takeHome.toPlainString(),
        "IDR",
    )

fun PayrollReview.toResponse() =
    PayrollReviewResponse(
        id,
        runId,
        number,
        runVersion,
        approvalId,
        referenceDate,
        totals.toResponse(),
        submittedBy,
        submittedAt,
        reason,
        status.name,
        version,
    )

fun PayrollReviewChange.toResponse() =
    PayrollReviewChangeResponse(
        revision,
        action.name,
        status.name,
        approvalVersion,
        step,
        actorId,
        decidingFor,
        reason,
        at,
    )

fun PayrollReviewDetails.toResponse() =
    PayrollReviewDetailsResponse(
        review.toResponse(),
        PayrollReviewApprovalResponse(
            approval.id,
            approval.status.name,
            approval.version,
            approval.currentStep,
            approval.templateId,
            approval.templateRevision,
            approval.stages.map { it.assignees },
            approval.authorId,
            approval.excludedAccountIds,
        ),
        changes.map { it.toResponse() },
    )
