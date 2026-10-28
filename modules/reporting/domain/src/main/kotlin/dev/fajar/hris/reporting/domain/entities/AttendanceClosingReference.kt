package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.util.UUID

data class AttendanceClosingReference(
    val periodId: UUID,
    val jobId: UUID,
    val version: Long,
    val closedAt: Instant,
    val timezone: String,
)
