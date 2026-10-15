package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollAmendmentSummary.toResponse() =
    PayrollAmendmentSummaryResponse(
        id,
        firstAssessmentId,
        firstMonth.toString(),
        PayrollEmployeeResponse(
            employee.id,
            employee.employeeNumber,
            employee.name,
            employee.employmentVersion,
        ),
        version,
        status.name,
        action.name,
        latestCalculationId,
        createdAt,
        actorId,
        recordedAt,
        reason,
        latestReviewId,
        latestPublicationId,
    )

fun PayrollAmendment.toResponse() =
    PayrollAmendmentResponse(
        summary.toResponse(),
        PayrollAmendmentTermsResponse(
            terms.sourceVersions.map { it.toResponse() },
            terms.corrections.map {
                PayrollAmendmentRemunerationResponse(
                    it.assessmentId,
                    it.compensation?.toResponse(),
                    it.input?.toResponse(),
                )
            },
        ),
    )

fun PayrollAmendmentCalculationSummary.toResponse() =
    PayrollAmendmentCalculationSummaryResponse(
        id,
        amendmentId,
        number,
        draftVersion,
        status.name,
        assessmentCount,
        difference?.toResponse(),
        failure?.let { PayrollRunFailureResponse(it.kind.name, it.code, it.fields, it.parameters) },
        actorId,
        recordedAt,
        reason,
    )

fun PayrollAmendmentDetails.toResponse() =
    PayrollAmendmentDetailsResponse(
        amendment.toResponse(),
        Page(history.items.map { it.toResponse() }, history.nextCursor),
        latestCalculation?.toResponse(),
        availableActions.map { it.name }.toSet(),
        latestReview?.toResponse(),
    )

fun PayrollAmendmentCalculationAttempt.toResponse() =
    PayrollAmendmentCalculationAttemptResponse(summary.toResponse(), preview?.toResponse())
