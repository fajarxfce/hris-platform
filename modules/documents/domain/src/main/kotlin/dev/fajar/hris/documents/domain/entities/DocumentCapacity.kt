package dev.fajar.hris.documents.domain.entities
data class DocumentCapacity(
    val documentCount: Int,
    val activeUploads: Int,
    val activeActorUploads: Int,
    val unfilledBytes: Long,
)
