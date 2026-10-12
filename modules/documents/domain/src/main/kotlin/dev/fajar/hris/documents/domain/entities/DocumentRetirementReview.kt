package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

/** Retention-only metadata; reviewing eligibility does not reserve it. */
data class DocumentRetirementReview(
    val revisionId: UUID,
    val revisionNumber: Int,
    val revisionVersion: Long,
    val status: DocumentRevisionStatus,
    val published: Boolean,
    val retention: DocumentRetentionState,
    val reviewedAt: Instant,
    val blockingReason: String?,
)
