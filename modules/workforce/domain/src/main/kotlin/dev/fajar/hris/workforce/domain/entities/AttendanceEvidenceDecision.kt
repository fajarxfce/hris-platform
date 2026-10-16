package dev.fajar.hris.workforce.domain.entities

import java.util.UUID

data class AttendanceEvidenceDecision(
    val eventId: UUID,
    val expectedVersion: Long,
    val decision: AttendanceReviewDecision,
)
