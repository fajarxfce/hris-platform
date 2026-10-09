package dev.fajar.hris.payroll.domain.entities

import java.time.YearMonth
import java.util.UUID

/**
 * Tax evidence from one immutable published result; it already retains cumulative prior history.
 */
data class PayrollTaxAssessment(
    val id: UUID,
    val employeeId: UUID,
    val month: YearMonth,
    val openingId: UUID,
    val openingRevision: Long,
    val registration: TaxRegistration,
    val input: IncomeTaxInput,
    val calculation: IncomeTaxCalculation,
)
