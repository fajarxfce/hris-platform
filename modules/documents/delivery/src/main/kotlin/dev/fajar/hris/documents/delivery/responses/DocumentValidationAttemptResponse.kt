package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentValidationAttempt
import java.time.Instant
import java.util.UUID

data class DocumentValidationAttemptResponse(
    val jobId: UUID,
    val number: Int,
    val actorId: UUID,
    val createdAt: Instant,
    val reason: String,
)

fun DocumentValidationAttempt.toResponse() =
    DocumentValidationAttemptResponse(jobId, number, actorId, createdAt, reason)
