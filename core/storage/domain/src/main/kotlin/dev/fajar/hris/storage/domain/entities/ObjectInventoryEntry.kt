package dev.fajar.hris.storage.domain.entities

import java.time.Instant

data class ObjectInventoryEntry(
    val key: String,
    val size: Long,
    val etag: String,
    val modifiedAt: Instant,
)
