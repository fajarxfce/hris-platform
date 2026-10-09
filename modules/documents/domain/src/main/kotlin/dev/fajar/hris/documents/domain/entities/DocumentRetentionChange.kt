package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

data class DocumentRetentionChange(
    val state: DocumentRetentionState,
    val action: DocumentRetentionAction,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
