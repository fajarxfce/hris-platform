package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentSource
import java.time.YearMonth
import tools.jackson.databind.ObjectMapper

fun PayrollAmendmentSourceRow.toAmendmentSource(json: ObjectMapper): PayrollAmendmentSource {
    val facts = json.readValue(this.facts, PayrollCalculationFactsData::class.java).toDomain()
    val calculation =
        json.readValue(this.calculation, PayrollMonthlyCalculationData::class.java).toDomain()
    val month = YearMonth.from(taxMonth)
    require(taxMonth.dayOfMonth == 1 && facts.month == month && calculation.taxInput.month == month)
    require(target.companyId == companyId && opening.revision.companyId == companyId)
    require(opening.employeeId == target.employmentId && opening.year == month.year)
    require(revision >= 0 && previousAssessmentRevision >= 0)
    require(target.previousAssessmentId != null || previousAssessmentRevision == 0L)
    require(
        opening.revision.openingId == target.taxOpeningId &&
            opening.revision.revision == target.taxOpeningRevision
    )
    return PayrollAmendmentSource(
        companyId,
        assessmentId,
        revision,
        personId,
        target.toTarget().copy(previousAssessmentRevision = previousAssessmentRevision),
        opening.toOpening(json),
        facts,
        calculation,
        workJobId,
        availableWorkAdjustment?.toWorkAdjustment(month),
    )
}
