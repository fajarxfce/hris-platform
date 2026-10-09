package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

/** Immutable evidence for one accepted revision; versions describe the committed result. */
data class DocumentRetirement(
    val documentId: UUID,
    val revisionId: UUID,
    val revisionVersion: Long,
    val retentionVersion: Long,
    val actorId: UUID,
    val retiredAt: Instant,
    val deleteAfter: Instant,
    val reason: String,
)
