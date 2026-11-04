package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Gateway authority must still be checked against the current organization/native parent on every
 * request.
 */
data class CollaborationSpaceGatewaySession(
    val id: UUID,
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val platform: CollaborationPlatform,
    val clientBuild: Int,
    val version: Long,
    val createdAt: Instant,
    val rotatedAt: Instant,
    val expiresAt: Instant,
    val accessExpiresAt: Instant,
    val revokedAt: Instant? = null,
) {
    override fun toString() = "CollaborationSpaceGatewaySession(<redacted>)"
}

data class CollaborationSpaceGatewayRefresh(
    val session: CollaborationSpaceGatewaySession,
    val previous: Boolean,
    val replayUntil: Instant?,
)

class CollaborationSpaceGatewayTokens(
    val sessionId: UUID,
    val version: Long,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
    val expiresAt: Instant,
    val replayUntil: Instant,
) {
    override fun toString() = "CollaborationSpaceGatewayTokens(<redacted>)"
}
