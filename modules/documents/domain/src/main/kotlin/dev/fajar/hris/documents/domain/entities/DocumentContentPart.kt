package dev.fajar.hris.documents.domain.entities
data class DocumentContentPart(
    val key: String,
    val offset: Long,
    val size: Int,
    val sha256: String,
    val etag: String,
)
