package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Internal provider bearer, never a public response or a participant E2EE key. */
class CollaborationProviderSession(
    val companyId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val generation: Long,
    val accessToken: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Null only for legacy stored credentials; never eligible for live provider use. */
    val expiresAt: Instant?,
) {
    override fun toString() = "CollaborationProviderSession(<redacted>)"
}
