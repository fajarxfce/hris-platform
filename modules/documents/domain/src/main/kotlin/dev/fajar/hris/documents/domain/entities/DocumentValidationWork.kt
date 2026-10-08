package dev.fajar.hris.documents.domain.entities
data class DocumentValidationWork(
    val revision: DocumentRevision,
    val chunks: List<DocumentUploadChunk>,
    val parts: List<DocumentContentPart>,
)
