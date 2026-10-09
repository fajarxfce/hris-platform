package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class NativePushRegistration(
    val sessionId: UUID,
    val accountId: UUID,
    val platform: PushPlatform,
    val enabled: Boolean,
    val version: Long,
    val registeredAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
)
