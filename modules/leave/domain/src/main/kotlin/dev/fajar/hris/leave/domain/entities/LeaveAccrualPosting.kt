package dev.fajar.hris.leave.domain.entities

import java.time.*
import java.util.UUID

data class LeaveAccrualPosting(
    val id: UUID,
    val employeeId: UUID,
    val typeId: UUID,
    val processedMonth: YearMonth,
    val award: LeaveAccrualAward,
    val frequency: LeaveAccrualFrequency,
    val policy: LeavePolicySnapshot,
    val employmentVersion: Long,
    val balanceVersion: Long,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
