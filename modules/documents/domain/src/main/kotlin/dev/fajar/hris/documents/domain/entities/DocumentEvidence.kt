package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

/** Metadata for a currently published, accepted revision; content remains separately authorized. */
data class DocumentEvidence(
    val documentId: UUID,
    val revisionId: UUID,
    val revisionNumber: Int,
    val title: String,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
    val validatedAt: Instant,
)
