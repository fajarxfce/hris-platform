package dev.fajar.hris.identity.domain.entities

import java.time.Instant
import java.util.UUID

data class NativeAccess(
    val sessionId: UUID,
    val accountId: UUID,
    val authenticatedAt: Instant,
    val mfaVerifiedAt: Instant?,
    val credentialVersion: Long,
    val sessionVersion: Long,
)
