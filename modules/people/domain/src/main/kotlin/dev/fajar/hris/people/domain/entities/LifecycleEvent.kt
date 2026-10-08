package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.util.UUID

data class LifecycleEvent(
    val version: Long,
    val taskKey: String?,
    val action: LifecycleAction,
    val assigneeId: UUID?,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
