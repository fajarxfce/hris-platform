package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

data class LeaveRequestChange(
    val version: Long,
    val kind: LeaveChangeKind,
    val status: LeaveStatus,
    val cancellationApprovalId: UUID?,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
