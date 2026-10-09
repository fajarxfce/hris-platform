package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class OvertimePay(
    val ruleId: String,
    val dayKind: OvertimeDayKind,
    val monthlyWage: BigDecimal,
    val displayHourlyWage: BigDecimal,
    val segments: List<OvertimePaySegment>,
    val amount: BigDecimal,
    val rounding: EarningsRounding,
)
