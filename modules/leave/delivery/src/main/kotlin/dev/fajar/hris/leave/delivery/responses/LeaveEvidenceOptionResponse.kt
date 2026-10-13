package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.util.UUID

data class LeaveEvidenceOptionResponse(
    val documentId: UUID,
    val revisionId: UUID,
    val revisionNumber: Int,
    val title: String,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val validatedAt: Instant,
)
