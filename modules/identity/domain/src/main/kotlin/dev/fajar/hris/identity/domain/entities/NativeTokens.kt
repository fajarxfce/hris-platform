package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class NativeTokens(
    val sessionId: UUID,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
    val sessionExpiresAt: Instant,
) {
    override fun toString(): String = "NativeTokens(<redacted>)"
}
