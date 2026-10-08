package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.util.UUID

data class LifecycleEventResponse(
    val version: Long,
    val taskKey: String?,
    val action: String,
    val assigneeId: UUID?,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
