package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollAmendmentReview.toResponse() =
    PayrollAmendmentReviewResponse(
        id,
        amendmentId,
        number,
        amendmentVersion,
        calculationId,
        approvalId,
        referenceDate,
        timezone,
        exposure.toPlainString(),
        "IDR",
        submittedBy,
        submittedAt,
        reason,
        status.name,
        version,
    )

fun PayrollAmendmentReviewChange.toResponse() =
    PayrollAmendmentReviewChangeResponse(
        revision,
        amendmentRevision,
        action.name,
        status.name,
        approvalVersion,
        step,
        actorId,
        decidingFor,
        reason,
        recordedAt,
    )

fun PayrollAmendmentReviewDetails.toResponse() =
    PayrollAmendmentReviewDetailsResponse(
        review.toResponse(),
        amendment.toResponse(),
        calculation.toResponse(),
        PayrollReviewApprovalResponse(
            approval.id,
            approval.status.name,
            approval.version,
            approval.currentStep,
            approval.templateId,
            approval.templateRevision,
            approval.stages.map { it.assignees },
            approval.authorId,
            approval.excludedAccountIds + listOfNotNull(approval.requesterId),
        ),
        history.map { it.toResponse() },
        accounts.map { PayrollReviewAccountResponse(it.id, it.displayName) },
        availableActions.map { it.name }.toSet(),
    )
