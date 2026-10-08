package dev.fajar.hris.identity.delivery.security

import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class SessionIdentity(
    override val accountId: UUID,
    override val authenticatedAt: Instant,
    val mfaConfigured: Boolean,
    override val credentialVersion: Long = 0,
    override val mfaVerifiedAt: Instant? = null,
) : AuthenticatedIdentity, Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }

    override fun getName(): String = accountId.toString()
}
