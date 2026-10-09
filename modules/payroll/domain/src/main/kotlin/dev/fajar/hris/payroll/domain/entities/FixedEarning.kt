package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class FixedEarning(
    val code: String,
    val name: String,
    val amount: BigDecimal,
    val taxable: Boolean,
)
