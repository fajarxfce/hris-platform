package dev.fajar.hris.leave.domain.entities

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class LeaveBatchReview(
    val kind: LeaveBatchKind,
    val period: YearMonth,
    val companyDate: LocalDate,
    val timezone: String,
    val type: LeaveType,
    val totalEmployees: Int,
    val employeeIds: List<UUID>,
)
