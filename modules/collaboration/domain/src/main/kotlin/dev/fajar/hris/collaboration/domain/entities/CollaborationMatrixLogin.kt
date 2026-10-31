package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Prepared under HRIS authority; never accepted from a public provider-login request. */
data class CollaborationMatrixLogin(
    val companyId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

/**
 * An internal candidate. The use case must fence publication; unused candidates expire without
 * deleting E2EE devices.
 */
class CollaborationMatrixCredential(
    val userId: String,
    val deviceId: String,
    val accessToken: String,
    val expiresAt: Instant,
) {
    override fun toString() = "CollaborationMatrixCredential(<redacted>)"
}
