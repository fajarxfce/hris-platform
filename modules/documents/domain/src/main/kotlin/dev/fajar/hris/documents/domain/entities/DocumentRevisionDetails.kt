package dev.fajar.hris.documents.domain.entities

data class DocumentRevisionDetails(
    val revision: DocumentRevision,
    val availableActions: List<DocumentRevisionAction>,
)
