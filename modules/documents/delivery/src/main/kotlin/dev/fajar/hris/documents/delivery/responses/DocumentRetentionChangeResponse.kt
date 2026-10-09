package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentRetentionChange
import java.time.Instant
import java.util.UUID

data class DocumentRetentionChangeResponse(
    val state: DocumentRetentionResponse,
    val action: String,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)

fun DocumentRetentionChange.toResponse() =
    DocumentRetentionChangeResponse(state.toResponse(), action.name, actorId, recordedAt, reason)
