package dev.fajar.hris.documents.delivery.requests

import dev.fajar.hris.documents.domain.entities.DocumentClassification
import java.util.UUID

data class StartDocumentUploadRequest(
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
