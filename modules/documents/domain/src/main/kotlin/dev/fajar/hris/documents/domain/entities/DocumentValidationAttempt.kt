package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

data class DocumentValidationAttempt(
    val jobId: UUID,
    val number: Int,
    val actorId: UUID,
    val createdAt: Instant,
    val reason: String,
)
