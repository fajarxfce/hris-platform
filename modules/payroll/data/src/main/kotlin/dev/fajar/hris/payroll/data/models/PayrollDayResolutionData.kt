package dev.fajar.hris.payroll.data.models

data class PayrollDayResolutionData(
    val workDate: String,
    val portion: String,
    val disposition: String,
    val reference: String,
)
