package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

data class LeaveAccrualScheduleEntry(
    val ordinal: Int,
    val batchId: UUID,
    val initialJobId: UUID,
    val scheduledFor: Instant,
)
