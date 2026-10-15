package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.time.YearMonth

data class LeaveAccrualPeriodReview(val period: YearMonth, val dueAt: Instant, val type: LeaveType)
