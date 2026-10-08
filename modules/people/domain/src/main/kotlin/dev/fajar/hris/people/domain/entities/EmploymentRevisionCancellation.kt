package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.util.UUID

data class EmploymentRevisionCancellation(
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
