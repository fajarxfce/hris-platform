package dev.fajar.hris.leave.delivery.responses

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class LeaveBatchReviewResponse(
    val kind: String,
    val period: YearMonth,
    val companyDate: LocalDate,
    val timezone: String,
    val type: LeaveTypeResponse,
    val totalEmployees: Int,
    val employeeIds: List<UUID>,
)
