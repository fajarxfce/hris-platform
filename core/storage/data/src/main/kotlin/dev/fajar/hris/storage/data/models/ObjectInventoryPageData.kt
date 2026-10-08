package dev.fajar.hris.storage.data.models

data class ObjectInventoryPageData(
    val entries: List<ObjectInventoryEntryData>,
    val hasMore: Boolean,
)
