package dev.fajar.hris.leave.domain.entities

import java.time.LocalDate
import java.util.UUID

data class LeaveAccrualScheduleReview(
    val companyDate: LocalDate,
    val timezone: String,
    val frequency: LeaveAccrualFrequency,
    val policyVersion: Long,
    val periods: List<LeaveAccrualPeriodReview>,
    val employeeIds: List<UUID>,
)
