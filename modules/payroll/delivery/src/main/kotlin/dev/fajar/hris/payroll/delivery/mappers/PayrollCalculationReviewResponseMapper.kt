package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.PayrollCalculationReview

fun PayrollCalculationReview.toResponse() =
    PayrollCalculationReviewResponse(
        period.toResponse(),
        source?.let {
            PayrollCalculationSourceResponse(
                it.id,
                it.month.toString(),
                it.version,
                it.closed,
                it.jobId,
                it.timezone,
            )
        },
        policy?.let {
            PayrollCalculationPolicyResponse(
                it.version,
                it.appliedRevision,
                it.effectiveFrom.toString(),
                it.effectiveUntil.toString(),
                it.incomeTaxRuleId,
                it.insuranceRuleId,
            )
        },
        runCount,
        blockingIssues.map {
            PayrollRunFailureResponse(it.kind.name, it.code, it.fields, it.parameters)
        },
        availableActions.map { it.name },
    )
