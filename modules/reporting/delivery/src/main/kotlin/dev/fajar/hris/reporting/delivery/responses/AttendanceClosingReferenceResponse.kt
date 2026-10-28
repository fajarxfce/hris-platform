package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.util.UUID

data class AttendanceClosingReferenceResponse(
    val periodId: UUID,
    val jobId: UUID,
    val version: Long,
    val closedAt: Instant,
    val timezone: String,
)
