package dev.fajar.hris.storage.data.models

import java.time.Instant

data class ObjectInventoryEntryData(
    val key: String,
    val size: Long,
    val etag: String,
    val modifiedAt: Instant,
)
