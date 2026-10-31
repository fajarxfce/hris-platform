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
) {
    override fun toString() = "CollaborationProviderSession(<redacted>)"
}
