package dev.fajar.hris.payroll.delivery.responses

data class PayrollAmendmentResponse(
    val summary: PayrollAmendmentSummaryResponse,
    val terms: PayrollAmendmentTermsResponse,
)
