package dev.fajar.hris.leave.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class LeaveAccrualScheduleReviewResponse(
    val companyDate: LocalDate,
    val timezone: String,
    val frequency: String,
    val policyVersion: Long,
    val periods: List<LeaveAccrualPeriodReviewResponse>,
    val employeeIds: List<UUID>,
)
