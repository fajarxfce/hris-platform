package dev.fajar.hris.sync.delivery.responses

import java.time.Instant

data class SyncChangesResponse(
    val items: List<SyncChangeResponse>,
    val cursor: String,
    val hasMore: Boolean,
    val pendingPublication: Boolean,
    val pollAfterSeconds: Int,
    val serverTime: Instant,
)
