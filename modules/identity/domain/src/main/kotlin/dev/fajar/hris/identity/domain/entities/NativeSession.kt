package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class NativeSession(
    val id: UUID,
    val accountId: UUID,
    val deviceName: String,
    val createdAt: Instant,
    val authenticatedAt: Instant,
    val mfaVerifiedAt: Instant?,
    val credentialVersion: Long,
    val expiresAt: Instant,
    val accessExpiresAt: Instant,
    val rotatedAt: Instant,
    val revokedAt: Instant?,
    val version: Long,
    val exchangeOperationId: UUID,
    val exchangeReplayUntil: Instant?,
)
