package dev.fajar.hris.sync.domain.entities

import java.time.Instant
import java.util.UUID

data class SyncHead(
    val epoch: UUID,
    val position: Long,
    val prunedThrough: Long,
    val publishedAt: Instant?,
)
