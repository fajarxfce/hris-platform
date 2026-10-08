package dev.fajar.hris.documents.domain.entities

import java.util.UUID

data class DocumentUploadLease(
    val revision: DocumentRevision,
    val chunk: DocumentUploadChunk,
    val attemptId: UUID,
    val key: String,
)
