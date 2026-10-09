package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class OvertimePayResponse(
    val ruleId: String,
    val dayKind: String,
    val monthlyWage: String,
    val displayHourlyWage: String,
    val segments: List<OvertimePaySegmentResponse>,
    val amount: String,
    val rounding: String,
)
