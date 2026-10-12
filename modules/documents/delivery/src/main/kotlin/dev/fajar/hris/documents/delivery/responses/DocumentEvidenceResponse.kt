package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentEvidence
import java.time.Instant
import java.util.UUID

data class DocumentEvidenceResponse(
    val documentId: UUID,
    val revisionId: UUID,
    val revisionNumber: Int,
    val title: String,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
    val validatedAt: Instant,
)

fun DocumentEvidence.toResponse() =
    DocumentEvidenceResponse(
        documentId,
        revisionId,
        revisionNumber,
        title,
        fileName,
        mediaType,
        size,
        sha256,
        validatedAt,
    )
