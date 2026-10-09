package dev.fajar.hris.payroll.delivery.responses

data class PayrollDayResolutionResponse(
    val workDate: String,
    val portion: String,
    val disposition: String,
    val reference: String,
)
