package dev.fajar.hris.core.http

data class BinaryDownloadMetadata(
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val etag: String,
)
