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
) : AuthenticatedIdentity, Serializable, org.springframework.security.oauth2.core.user.OAuth2User {
    private companion object {
        private const val serialVersionUID = 1L
    }

    override fun getName(): String = accountId.toString()

    override fun getAttributes(): Map<String, Any> = mapOf("sub" to accountId.toString())

    override fun getAuthorities(): Collection<org.springframework.security.core.GrantedAuthority> =
        listOf(org.springframework.security.core.authority.SimpleGrantedAuthority("SESSION"))
}
