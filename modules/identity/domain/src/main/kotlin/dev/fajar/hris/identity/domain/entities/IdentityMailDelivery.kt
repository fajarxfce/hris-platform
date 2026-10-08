package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class IdentityMailDelivery(
    val challengeId: UUID,
    val accountId: UUID,
    val state: IdentityMailState,
    val attempts: Int,
    val availableAt: Instant,
    val expiresAt: Instant,
    val leaseUntil: Instant?,
    val deliveredAt: Instant?,
    val failureCode: String?,
)
