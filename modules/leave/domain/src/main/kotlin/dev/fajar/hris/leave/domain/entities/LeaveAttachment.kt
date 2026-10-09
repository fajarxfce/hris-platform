package dev.fajar.hris.leave.domain.entities

import java.util.UUID

/** Accepted revision metadata frozen with a submitted request. */
data class LeaveAttachment(
    val documentId: UUID,
    val revisionId: UUID,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
)
