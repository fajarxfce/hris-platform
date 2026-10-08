package dev.fajar.hris.workforce.delivery.requests

import dev.fajar.hris.workforce.domain.entities.AttendanceReviewDecision

data class AttendanceReviewRequest(
    val version: Long,
    val decision: AttendanceReviewDecision,
    val reason: String,
)
