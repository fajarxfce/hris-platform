package dev.fajar.hris.workforce.domain.entities

import java.time.Instant
import java.util.UUID

data class AttendanceReview(
    val actorId: UUID,
    val decision: AttendanceReviewDecision,
    val reviewedAt: Instant,
    val reason: String,
)
