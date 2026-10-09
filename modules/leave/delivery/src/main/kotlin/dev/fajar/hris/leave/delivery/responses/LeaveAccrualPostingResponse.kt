package dev.fajar.hris.leave.delivery.responses

import java.time.*
import java.util.UUID

data class LeaveAccrualPostingResponse(
    val id: UUID,
    val employeeId: UUID,
    val typeId: UUID,
    val processedMonth: YearMonth,
    val period: YearMonth,
    val eligibleFrom: LocalDate,
    val eligibleUntil: LocalDate,
    val days: String,
    val frequency: String,
    val policy: LeavePolicySnapshotResponse,
    val employmentVersion: Long,
    val balanceVersion: Long,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
