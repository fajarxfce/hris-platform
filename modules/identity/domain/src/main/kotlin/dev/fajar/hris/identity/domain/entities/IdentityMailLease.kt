package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class IdentityMailLease(
    val challengeId: UUID,
    val accountId: UUID,
    val owner: UUID,
    val token: UUID,
    val attempts: Int,
    val expiresAt: Instant,
)
