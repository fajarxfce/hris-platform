package dev.fajar.hris.workforce.delivery.responses

import java.time.Instant
import java.util.UUID

data class AttendanceReviewResponse(
    val actorId: UUID,
    val decision: String,
    val reviewedAt: Instant,
    val reason: String,
)
