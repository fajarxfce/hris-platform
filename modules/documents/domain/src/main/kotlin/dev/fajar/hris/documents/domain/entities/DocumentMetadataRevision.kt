package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

data class DocumentMetadataRevision(
    val documentId: UUID,
    val version: Long,
    val documentVersion: Long,
    val metadata: DocumentBusinessMetadata,
    val actorId: UUID?,
    val recordedAt: Instant,
    val reason: String?,
)
