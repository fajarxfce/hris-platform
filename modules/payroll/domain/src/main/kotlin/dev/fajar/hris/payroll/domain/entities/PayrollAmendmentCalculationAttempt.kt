package dev.fajar.hris.payroll.domain.entities

data class PayrollAmendmentCalculationAttempt(
    val summary: PayrollAmendmentCalculationSummary,
    val preview: PayrollAmendmentPreview?,
)
