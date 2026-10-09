package dev.fajar.hris.payroll.data.models

import java.time.*

data class OvertimePayData(
    val ruleId: String,
    val dayKind: String,
    val monthlyWage: String,
    val displayHourlyWage: String,
    val segments: List<OvertimePaySegmentData>,
    val amount: String,
    val rounding: String,
)
