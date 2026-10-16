package dev.fajar.hris.identity.delivery.responses

import java.time.Instant
import java.util.UUID

data class RoleTemplateRevisionResponse(
    val revision: Long,
    val code: String,
    val name: String,
    val permissions: Set<String>,
    val active: Boolean,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
