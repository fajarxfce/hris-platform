package dev.fajar.hris.documents.delivery.requests
data class ValidateDocumentRequest(val expectedVersion: Long, val reason: String)
