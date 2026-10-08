package dev.fajar.hris.identity.delivery.security

import java.time.Instant
import java.util.UUID

data class NativeIdentity(
    val sessionId: UUID,
    val sessionVersion: Long,
    override val accountId: UUID,
    override val authenticatedAt: Instant,
    override val credentialVersion: Long,
    override val mfaVerifiedAt: Instant?,
) : AuthenticatedIdentity
