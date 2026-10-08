package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

data class DocumentInventoryReference(
    val key: String,
    val attemptId: UUID,
    val revisionId: UUID,
    val size: Long,
    val currentAttemptId: UUID?,
    val revisionStatus: DocumentRevisionStatus,
    val expiresAt: Instant,
    val cleanupRegistered: Boolean,
    val recoveryCount: Int,
)
