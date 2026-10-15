package dev.fajar.hris.payroll.delivery.responses

data class PayrollAmendmentPublicationDetailsResponse(
    val publication: PayrollAmendmentPublicationResponse,
    val job: PayrollRunProgressResponse,
    val amendment: PayrollAmendmentSummaryResponse,
    val availableActions: List<String>,
)
