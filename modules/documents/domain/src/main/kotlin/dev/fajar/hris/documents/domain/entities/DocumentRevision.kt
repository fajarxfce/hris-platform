package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

data class DocumentRevision(
    val id: UUID,
    val documentId: UUID,
    val number: Int,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
    val status: DocumentRevisionStatus,
    val uploadedBytes: Long,
    val version: Long,
    val createdBy: UUID,
    val createdAt: Instant,
    val expiresAt: Instant,
    val reason: String,
    val validationJobId: UUID? = null,
    val validationAttempts: Int = 0,
    val failureCode: String? = null,
    val inspection: DocumentInspection? = null,
    val validatedAt: Instant? = null,
)
