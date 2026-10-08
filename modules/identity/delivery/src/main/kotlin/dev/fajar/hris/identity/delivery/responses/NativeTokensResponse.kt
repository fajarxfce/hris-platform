package dev.fajar.hris.identity.delivery.responses

import java.util.UUID

data class NativeTokensResponse(
    val sessionId: UUID,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: String,
    val sessionExpiresAt: String,
    val tokenType: String = "Bearer",
) {
    override fun toString(): String = "NativeTokensResponse(<redacted>)"
}
