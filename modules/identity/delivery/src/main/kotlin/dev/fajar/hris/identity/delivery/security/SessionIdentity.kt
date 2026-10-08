package dev.fajar.hris.identity.delivery.security

import java.io.Serializable
import java.security.Principal
import java.time.Instant
import java.util.UUID

data class SessionIdentity(
    val accountId: UUID,
    val authenticatedAt: Instant,
    val mfaConfigured: Boolean,
    val credentialVersion: Long = 0,
    val mfaVerifiedAt: Instant? = null,
) : Principal, Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }

    override fun getName(): String = accountId.toString()
}
