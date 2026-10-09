package dev.fajar.hris.payroll.data.models

import java.time.*

data class ProratedPayData(
    val monthlyAmount: String,
    val totalUnits: String,
    val payableUnits: String,
    val amount: String,
    val rounding: String,
)
