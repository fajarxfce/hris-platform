package dev.fajar.hris.leave.delivery.responses

import java.util.UUID

data class LeaveAttachmentResponse(
    val documentId: UUID,
    val revisionId: UUID,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
)
