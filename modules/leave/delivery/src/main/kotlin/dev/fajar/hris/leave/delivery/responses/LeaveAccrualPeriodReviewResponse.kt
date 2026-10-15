package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.time.YearMonth

data class LeaveAccrualPeriodReviewResponse(
    val period: YearMonth,
    val dueAt: Instant,
    val type: LeaveTypeResponse,
)
