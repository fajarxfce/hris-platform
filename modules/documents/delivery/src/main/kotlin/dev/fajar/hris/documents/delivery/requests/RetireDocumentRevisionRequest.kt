package dev.fajar.hris.documents.delivery.requests

data class RetireDocumentRevisionRequest(
    val expectedRevisionVersion: Long,
    val expectedRetentionVersion: Long,
    val reason: String,
)
