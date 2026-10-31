package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * A bounded admission observation, not a bearer token or permanent permission. Exchange and
 * provider responses require reacquisition of current authority.
 */
data class CollaborationAdmission(
    val companyId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val deviceVersion: Long,
    val nativeSessionId: UUID,
    val nativeSessionVersion: Long,
    val credentialVersion: Long,
    val admittedAt: Instant,
    val expiresAt: Instant,
)
