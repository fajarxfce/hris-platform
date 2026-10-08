package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentRevision
import dev.fajar.hris.documents.domain.policies.DOCUMENT_CHUNK_BYTES
import java.time.Instant
import java.util.UUID

data class DocumentRevisionResponse(
    val id: UUID,
    val documentId: UUID,
    val number: Int,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
    val status: String,
    val uploadedBytes: Long,
    val chunkSize: Int,
    val version: Long,
    val createdBy: UUID,
    val createdAt: Instant,
    val expiresAt: Instant,
    val reason: String,
)

fun DocumentRevision.toResponse() =
    DocumentRevisionResponse(
        id,
        documentId,
        number,
        fileName,
        mediaType,
        size,
        sha256,
        status.name,
        uploadedBytes,
        DOCUMENT_CHUNK_BYTES,
        version,
        createdBy,
        createdAt,
        expiresAt,
        reason,
    )
