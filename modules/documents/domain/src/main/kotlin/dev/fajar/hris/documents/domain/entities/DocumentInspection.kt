package dev.fajar.hris.documents.domain.entities
data class DocumentInspection(
    val size: Long,
    val sha256: String,
    val mediaType: String,
    val clean: Boolean,
    val engineVersion: String,
)
