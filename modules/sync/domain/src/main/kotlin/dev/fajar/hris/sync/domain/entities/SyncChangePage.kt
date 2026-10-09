package dev.fajar.hris.sync.domain.entities

import java.time.Instant

data class SyncChangePage(
    val items: List<SyncChange>,
    val cursor: String,
    val hasMore: Boolean,
    val pendingPublication: Boolean,
    val serverTime: Instant,
    val pollAfterSeconds: Int = 5,
)
