package dev.fajar.hris.documents.data.models

import java.time.OffsetDateTime
import java.util.UUID

data class DocumentInventoryReferenceData(
    val key: String,
    val attemptId: UUID,
    val revisionId: UUID,
    val size: Long,
    val currentAttemptId: UUID?,
    val revisionStatus: String,
    val expiresAt: OffsetDateTime,
    val cleanupRegistered: Boolean,
    val recoveryCount: Int,
)
