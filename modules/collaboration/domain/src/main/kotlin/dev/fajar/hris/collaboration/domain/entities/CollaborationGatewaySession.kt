package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Gateway authority must still be checked against the current HRIS parent on every request. */
data class CollaborationGatewaySession(
    val id: UUID,
    val companyId: UUID,
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
)

data class CollaborationGatewayRefresh(
    val session: CollaborationGatewaySession,
    val previous: Boolean,
    val replayUntil: Instant?,
)

class CollaborationGatewayTokens(
    val sessionId: UUID,
    val version: Long,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
    val expiresAt: Instant,
) {
    override fun toString() = "CollaborationGatewayTokens(<redacted>)"
}
