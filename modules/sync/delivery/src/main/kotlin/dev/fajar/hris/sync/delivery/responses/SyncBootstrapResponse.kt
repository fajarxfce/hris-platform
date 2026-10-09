package dev.fajar.hris.sync.delivery.responses

import java.time.Instant

data class SyncBootstrapResponse(
    val collections: List<String>,
    val items: List<SyncResourceResponse>,
    val nextCursor: String?,
    val changesCursor: String?,
    val serverTime: Instant,
)
