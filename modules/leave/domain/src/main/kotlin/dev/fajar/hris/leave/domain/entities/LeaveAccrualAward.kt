package dev.fajar.hris.leave.domain.entities

import java.time.LocalDate
import java.time.YearMonth

data class LeaveAccrualAward(
    val period: YearMonth,
    val eligibleFrom: LocalDate,
    val eligibleUntil: LocalDate,
    val halfDays: Int,
)
