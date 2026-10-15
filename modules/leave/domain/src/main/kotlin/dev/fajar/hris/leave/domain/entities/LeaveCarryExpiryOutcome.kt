package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

data class LeaveCarryExpiryOutcome(
    val ordinal: Int,
    val employeeId: UUID,
    val jobId: UUID,
    val status: LeaveCarryExpiryOutcomeStatus,
    val halfDays: Int,
    val ledgerId: UUID?,
    val recordedAt: Instant,
)
