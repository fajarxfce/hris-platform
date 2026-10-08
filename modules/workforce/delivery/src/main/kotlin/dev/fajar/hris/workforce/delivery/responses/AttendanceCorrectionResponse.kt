package dev.fajar.hris.workforce.delivery.responses

import java.time.Instant
import java.util.UUID

data class AttendanceCorrectionResponse(
    val id: UUID,
    val clockIn: Instant?,
    val clockOut: Instant?,
    val breakMinutes: Int,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
    val version: Long,
)
