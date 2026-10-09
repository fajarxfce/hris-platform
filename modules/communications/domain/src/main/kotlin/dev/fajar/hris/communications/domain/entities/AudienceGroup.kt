package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

data class AudienceGroup(
    val id: UUID,
    val version: Long,
    val name: String,
    val active: Boolean,
    val employmentIds: Set<UUID>,
    val recordedAt: Instant,
    val recordedBy: UUID,
    val reason: String,
)
