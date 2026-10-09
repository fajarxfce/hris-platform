package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.LocalDate

data class HolidayAllowance(
    val ruleId: String,
    val holidayDate: LocalDate,
    val dueDate: LocalDate,
    val service: HolidayService,
    val monthlyWage: BigDecimal,
    val statutoryAmount: BigDecimal,
    val employerTopUp: BigDecimal,
    val amount: BigDecimal,
    val rounding: EarningsRounding,
)
