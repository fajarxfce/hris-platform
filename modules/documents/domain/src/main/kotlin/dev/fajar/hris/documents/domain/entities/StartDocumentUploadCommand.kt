package dev.fajar.hris.documents.domain.entities

import java.util.UUID

data class StartDocumentUploadCommand(
    val documentId: UUID,
    val revisionId: UUID,
    val employmentId: UUID,
    val title: String,
    val classification: DocumentClassification,
    val expectedDocumentVersion: Long,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
    val reason: String,
)
