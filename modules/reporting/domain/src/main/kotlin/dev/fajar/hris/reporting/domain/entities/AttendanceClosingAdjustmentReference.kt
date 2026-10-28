package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.util.UUID

data class AttendanceClosingAdjustmentReference(
    val id: UUID,
    val revision: Long,
    val recordedAt: Instant,
)
