package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

/** Published personal evidence metadata only; selection grants no content access. */
data class LeaveEvidenceOption(
    val documentId: UUID,
    val revisionId: UUID,
    val revisionNumber: Int,
    val title: String,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val validatedAt: Instant,
)
