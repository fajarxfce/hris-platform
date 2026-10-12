package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

/** Original command reference for the current operator's uncommitted file part. */
data class DocumentUploadContinuation(
    val operationId: UUID,
    val offset: Long,
    val size: Int,
    val sha256: String,
    val attempts: Int,
    val retryAt: Instant?,
)
