package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.LocalDate

data class PayrollPayUnits(
    val total: BigDecimal,
    val payable: BigDecimal,
    val unpaid: BigDecimal,
    val employedDates: Set<LocalDate>,
)
