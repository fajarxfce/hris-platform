package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class ProratedPayResponse(
    val monthlyAmount: String,
    val totalUnits: String,
    val payableUnits: String,
    val amount: String,
    val rounding: String,
)
