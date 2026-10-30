package dev.fajar.hris.documents.delivery.requests

data class SaveDocumentMetadataRequest(
    val expectedVersion: Long,
    val metadata: DocumentBusinessMetadataRequest,
    val reason: String,
)
