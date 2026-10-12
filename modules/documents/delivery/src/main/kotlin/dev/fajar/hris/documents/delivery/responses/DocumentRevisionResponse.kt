package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentRevision
import dev.fajar.hris.documents.domain.entities.DocumentRevisionDetails
import dev.fajar.hris.documents.domain.policies.DOCUMENT_CHUNK_BYTES
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
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
    val validationJobId: UUID?,
    val validationAttempts: Int,
    val failureCode: String?,
    val validatedAt: Instant?,
    @get:ArraySchema(
        schema = Schema(allowableValues = ["VALIDATE", "CANCEL_UPLOAD"]),
        maxItems = 2,
        uniqueItems = true,
    )
    val availableActions: List<String> = emptyList(),
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
        validationJobId,
        validationAttempts,
        failureCode,
        validatedAt,
    )

fun DocumentRevisionDetails.toResponse(): DocumentRevisionResponse =
    revision.toResponse().copy(availableActions = availableActions.map { it.name })
