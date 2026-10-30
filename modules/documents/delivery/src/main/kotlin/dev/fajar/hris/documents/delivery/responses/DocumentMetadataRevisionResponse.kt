package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentMetadataRevision
import java.time.Instant
import java.util.UUID

data class DocumentMetadataRevisionResponse(
    val documentId: UUID,
    val version: Long,
    val documentVersion: Long,
    val metadata: DocumentBusinessMetadataResponse,
    val actorId: UUID?,
    val recordedAt: Instant,
    val reason: String?,
)

fun DocumentMetadataRevision.toResponse() =
    DocumentMetadataRevisionResponse(
        documentId,
        version,
        documentVersion,
        metadata.toResponse(),
        actorId,
        recordedAt,
        reason,
    )
