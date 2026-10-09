package dev.fajar.hris.payroll.delivery.responses

data class PayrollFinalizationDetailsResponse(
    val finalization: PayrollFinalizationResponse,
    val job: PayrollRunProgressResponse,
)
