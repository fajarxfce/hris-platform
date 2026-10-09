package dev.fajar.hris.sync.domain.entities

import java.time.Instant

data class SyncBootstrapPage(
    val items: List<SyncResource>,
    val nextCursor: String?,
    val changesCursor: String?,
    val collections: Set<SyncCollection>,
    val serverTime: Instant,
)
