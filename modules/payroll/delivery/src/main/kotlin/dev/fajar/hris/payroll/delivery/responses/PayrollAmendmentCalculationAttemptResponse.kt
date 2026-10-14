package dev.fajar.hris.payroll.delivery.responses

data class PayrollAmendmentCalculationAttemptResponse(
    val summary: PayrollAmendmentCalculationSummaryResponse,
    val preview: PayrollAmendmentPreviewResponse?,
)
