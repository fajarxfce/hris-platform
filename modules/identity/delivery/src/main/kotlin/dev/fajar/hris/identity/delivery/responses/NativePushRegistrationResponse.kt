package dev.fajar.hris.identity.delivery.responses

import java.time.Instant
import java.util.UUID

data class NativePushRegistrationResponse(
    val sessionId: UUID,
    val platform: String,
    val enabled: Boolean,
    val version: Long,
    val registeredAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
)
