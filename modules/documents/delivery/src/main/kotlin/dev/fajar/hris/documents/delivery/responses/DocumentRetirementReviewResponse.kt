package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentRetirementReview
import dev.fajar.hris.documents.domain.entities.DocumentRevisionStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

data class DocumentRetirementReviewResponse(
    val revisionId: UUID,
    val revisionNumber: Int,
    val revisionVersion: Long,
    val status: DocumentRevisionStatus,
    val published: Boolean,
    val retention: DocumentRetentionResponse,
    val reviewedAt: Instant,
    @get:Schema(
        description =
            "Current business blocker; null means eligible at reviewedAt. The command rechecks eligibility.",
        maxLength = 64,
        allowableValues =
            [
                "document_revision_not_ready",
                "document_not_archived",
                "document_legal_hold",
                "document_retention_indefinite",
                "document_retention_pending",
                "document_evidence_referenced",
                "document_retention_history_limit",
            ],
    )
    val blockingReason: String?,
)

fun DocumentRetirementReview.toResponse() =
    DocumentRetirementReviewResponse(
        revisionId,
        revisionNumber,
        revisionVersion,
        status,
        published,
        retention.toResponse(),
        reviewedAt,
        blockingReason,
    )
