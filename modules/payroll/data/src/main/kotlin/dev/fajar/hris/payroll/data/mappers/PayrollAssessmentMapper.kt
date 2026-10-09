package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.PayrollTaxAssessment
import java.time.YearMonth
import tools.jackson.databind.ObjectMapper

fun PayrollTaxAssessmentRow.toTaxAssessment(json: ObjectMapper) =
    PayrollTaxAssessment(
        id,
        employeeId,
        YearMonth.from(month),
        openingId,
        openingRevision,
        json.readValue(registration, TaxRegistrationData::class.java).toRegistration(),
        json.readValue(input, IncomeTaxInputData::class.java).toDomain(),
        json.readValue(calculation, IncomeTaxCalculationData::class.java).toDomain(),
    )
