package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

data class AudienceGroupSummary(
    val id: UUID,
    val version: Long,
    val name: String,
    val active: Boolean,
    val memberCount: Int,
    val recordedAt: Instant,
)
