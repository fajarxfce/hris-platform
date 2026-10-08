package dev.fajar.hris.documents.delivery.requests
data class CancelDocumentUploadRequest(val expectedVersion: Long, val reason: String)
