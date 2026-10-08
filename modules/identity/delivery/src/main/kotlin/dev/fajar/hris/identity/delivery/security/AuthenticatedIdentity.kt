package dev.fajar.hris.identity.delivery.security

import java.security.Principal
import java.time.Instant
import java.util.UUID

interface AuthenticatedIdentity : Principal {
    val accountId: UUID
    val authenticatedAt: Instant
    val credentialVersion: Long
    val mfaVerifiedAt: Instant?

    override fun getName(): String = accountId.toString()
}
