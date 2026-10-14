package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.util.UUID

/** Recalculate one chronological suffix; retained monthly rules still own every amount. */
fun recalculatePayrollAmendment(
    firstAssessmentId: UUID,
    sources: List<PayrollAmendmentSource>,
    corrections: List<PayrollAmendmentCorrection>,
): Result<PayrollAmendmentPreview> {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    if (
        sources.size !in 1..12 ||
            sources.map { it.companyId }.distinct().size != 1 ||
            sources.map { it.assessmentId }.distinct().size != sources.size ||
            sources.map { it.personId }.distinct().size != 1 ||
            sources.map { it.facts.month.year }.distinct().size != 1 ||
            sources.zipWithNext().any { (before, after) ->
                before.facts.month >= after.facts.month
            } ||
            sources.any {
                it.revision < 0 ||
                    it.revision == Long.MAX_VALUE ||
                    it.target.employeeId != it.opening.employeeId ||
                    it.opening.status != PayrollTaxOpeningStatus.VERIFIED ||
                    it.facts.month != it.calculation.taxInput.month
            }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_sources_invalid"))
    val first = sources.indexOfFirst { it.assessmentId == firstAssessmentId }
    if (first < 0)
        return Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_assessment_not_found"))
    val affected = sources.drop(first)
    if (
        corrections.size !in 1..affected.size ||
            corrections.map { it.assessmentId }.distinct().size != corrections.size ||
            corrections.none { it.assessmentId == firstAssessmentId } ||
            corrections.any { correction ->
                affected.none { it.assessmentId == correction.assessmentId }
            }
    )
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_payroll_amendment_corrections")
        )
    val proposed = corrections.associateBy { it.assessmentId }
    val prior = sources.getOrNull(first - 1)
    var previous =
        prior?.let {
            PayrollTaxAssessment(
                it.assessmentId,
                it.target.employeeId,
                it.facts.month,
                it.opening.id,
                it.opening.version,
                it.facts.compensation.tax,
                it.calculation.taxInput,
                it.calculation.tax,
            )
        }
    val calculations = mutableListOf<PayrollAmendmentCalculation>()
    var total = PayrollAmendmentDifference()
    for (source in affected) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val basis = proposed[source.assessmentId]?.facts ?: source.facts
        if (basis.month != source.facts.month)
            return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_amendment_month_changed"))
        val history =
            derivePayrollTaxHistory(
                source.target,
                basis.month,
                source.opening,
                basis.compensation.tax,
                previous,
            )
        if (history is Result.Failed)
            return Result.Failed(
                history.failure.copy(
                    parameters =
                        history.failure.parameters +
                            mapOf(
                                "assessmentId" to source.assessmentId.toString(),
                                "taxMonth" to basis.month.toString(),
                            )
                )
            )
        val facts = basis.copy(taxHistory = (history as Result.Success).value)
        val calculated = calculateMonthlyPayroll(facts)
        if (calculated is Result.Failed)
            return Result.Failed(
                calculated.failure.copy(
                    parameters =
                        calculated.failure.parameters +
                            mapOf(
                                "assessmentId" to source.assessmentId.toString(),
                                "taxMonth" to basis.month.toString(),
                            )
                )
            )
        val result = (calculated as Result.Success).value
        val difference =
            PayrollAmendmentDifference(
                result.tax.taxableGross - source.calculation.tax.taxableGross,
                result.tax.withheld - source.calculation.tax.withheld,
                result.tax.takeHome - source.calculation.tax.takeHome,
                result.employeeDeductions - source.calculation.employeeDeductions,
                result.employerCost - source.calculation.employerCost,
            )
        calculations +=
            PayrollAmendmentCalculation(
                PayrollAmendmentSourceReference(source.assessmentId, source.revision),
                source.target,
                source.calculation,
                facts,
                result,
                difference,
            )
        total =
            PayrollAmendmentDifference(
                total.taxableGross + difference.taxableGross,
                total.withheld + difference.withheld,
                total.takeHome + difference.takeHome,
                total.employeeDeductions + difference.employeeDeductions,
                total.employerCost + difference.employerCost,
            )
        previous =
            PayrollTaxAssessment(
                source.assessmentId,
                source.target.employeeId,
                basis.month,
                source.opening.id,
                source.opening.version,
                basis.compensation.tax,
                result.taxInput,
                result.tax,
            )
    }
    return Result.Success(
        PayrollAmendmentPreview(
            firstAssessmentId,
            java.util.List.copyOf(
                sources.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) }
            ),
            java.util.List.copyOf(calculations),
            total,
        )
    )
}
