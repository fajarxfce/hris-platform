package dev.fajar.hris.leave.domain.entities

import java.time.YearMonth
import java.util.UUID

data class ScheduleLeaveAccrualCommand(
    val id: UUID,
    val typeId: UUID,
    val firstPeriod: YearMonth,
    val periods: Int,
    val expectedPolicyVersion: Long,
    val employeeIds: Set<UUID>,
    val reason: String,
)
