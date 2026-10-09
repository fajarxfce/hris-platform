package dev.fajar.hris.payroll.data.models

data class PayrollHolidayInputData(
    val kind: String,
    val holidayDate: String,
    val continuousServiceFrom: String,
    val priorPayment: String,
    val reviewReference: String,
    val promisedAmount: String?,
)
