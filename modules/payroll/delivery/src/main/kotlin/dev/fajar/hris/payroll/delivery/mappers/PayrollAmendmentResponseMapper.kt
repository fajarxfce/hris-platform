package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollAmendmentSourceReference.toResponse() =
    PayrollAmendmentSourceReferenceResponse(assessmentId, revision)

fun PayrollAmendmentPreparation.toResponse() =
    PayrollAmendmentPreparationResponse(
        firstAssessmentId,
        java.util.List.copyOf(
            sources.map { PayrollAmendmentSourceReferenceResponse(it.assessmentId, it.revision) }
        ),
        java.util.List.copyOf(
            sources.map {
                PayrollAmendmentBasisResponse(
                    PayrollAmendmentSourceReferenceResponse(it.assessmentId, it.revision),
                    PayrollEmployeeResponse(
                        it.target.employeeId,
                        it.target.employeeNumber,
                        it.target.employeeName,
                        it.target.employmentVersion,
                    ),
                    it.facts.month.toString(),
                    it.facts.compensation.toResponse(),
                    it.facts.input.toResponse(),
                    it.calculation.toCalculationResponse(),
                    it.facts.workAdjustmentId,
                    it.availableWorkAdjustment?.toResponse(),
                    java.util.List.copyOf(
                        it.facts.workDays.map { day -> day.toCalculationResponse() }
                    ),
                )
            }
        ),
    )

fun PayrollAmendmentDifference.toResponse() =
    PayrollAmendmentDifferenceResponse(
        taxableGross.toPlainString(),
        withheld.toPlainString(),
        takeHome.toPlainString(),
        employeeDeductions.toPlainString(),
        employerCost.toPlainString(),
    )

fun PayrollAmendmentPreview.toResponse() =
    PayrollAmendmentPreviewResponse(
        firstAssessmentId,
        java.util.List.copyOf(sourceVersions.map { it.toResponse() }),
        java.util.List.copyOf(
            calculations.map {
                PayrollAmendmentCalculationResponse(
                    it.source.toResponse(),
                    PayrollEmployeeResponse(
                        it.target.employeeId,
                        it.target.employeeNumber,
                        it.target.employeeName,
                        it.target.employmentVersion,
                    ),
                    it.facts.month.toString(),
                    it.facts.compensation.toResponse(),
                    it.facts.input.toResponse(),
                    it.original.toCalculationResponse(),
                    it.calculation.toCalculationResponse(),
                    it.difference.toResponse(),
                    it.facts.workAdjustmentId,
                    java.util.List.copyOf(
                        it.facts.workDays.map { day -> day.toCalculationResponse() }
                    ),
                )
            }
        ),
        difference.toResponse(),
    )
