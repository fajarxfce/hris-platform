package dev.fajar.hris.documents.domain.entities

import java.util.UUID

data class DocumentDownload(
    val revisionId: UUID,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
)
