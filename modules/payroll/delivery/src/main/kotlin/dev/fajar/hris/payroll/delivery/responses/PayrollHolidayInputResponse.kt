package dev.fajar.hris.payroll.delivery.responses

data class PayrollHolidayInputResponse(
    val kind: String,
    val holidayDate: String,
    val continuousServiceFrom: String,
    val priorPayment: String,
    val reviewReference: String,
    val promisedAmount: String?,
)
