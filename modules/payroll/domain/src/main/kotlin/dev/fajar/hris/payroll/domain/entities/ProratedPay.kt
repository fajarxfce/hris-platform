package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class ProratedPay(
    val monthlyAmount: BigDecimal,
    val totalUnits: BigDecimal,
    val payableUnits: BigDecimal,
    val amount: BigDecimal,
    val rounding: EarningsRounding,
)
