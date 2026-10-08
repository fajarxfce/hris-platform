package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

data class DocumentUploadChunk(
    val id: UUID,
    val revisionId: UUID,
    val number: Int,
    val offset: Long,
    val size: Int,
    val sha256: String,
    val createdBy: UUID,
    val committed: Boolean,
    val attempts: Int,
    val attemptId: UUID?,
    val key: String?,
    val leaseUntil: Instant?,
    val etag: String?,
    val committedVersion: Long?,
)
