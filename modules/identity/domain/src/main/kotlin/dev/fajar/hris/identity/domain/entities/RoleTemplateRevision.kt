package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class RoleTemplateRevision(
    val revision: Long,
    val code: String,
    val name: String,
    val permissions: Set<String>,
    val active: Boolean,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
