package dev.fajar.hris.documents.delivery.requests

data class ChangeDocumentRetentionRequest(val expectedVersion: Long?, val reason: String)
