package dev.fajar.hris.payroll.domain.entities

data class PayrollAmendmentCalculation(
    val source: PayrollAmendmentSourceReference,
    val target: PayrollRunTarget,
    val original: PayrollMonthlyCalculation,
    val facts: PayrollCalculationFacts,
    val calculation: PayrollMonthlyCalculation,
    val difference: PayrollAmendmentDifference,
)
