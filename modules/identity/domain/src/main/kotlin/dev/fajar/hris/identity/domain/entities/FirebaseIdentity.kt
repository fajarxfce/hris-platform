package dev.fajar.hris.identity.domain.entities

import java.time.Duration
import java.time.Instant

/** Claims returned only after the data adapter verifies the pinned Google signing key. */
data class FirebaseIdentity(
    val issuer: String,
    val subject: String,
    val audience: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val authenticatedAt: Instant,
    val signInProvider: String,
    val tenantId: String? = null,
) {
    override fun toString() = "FirebaseIdentity(<redacted>)"
}

data class FirebaseSignInPolicy(
    val enabled: Boolean = false,
    val projectId: String = "",
    val recentAuthenticationAge: Duration = Duration.ofMinutes(10),
) {
    init {
        require(!enabled || Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]").matches(projectId))
        require(
            recentAuthenticationAge > Duration.ZERO &&
                recentAuthenticationAge <= Duration.ofHours(1)
        )
    }

    val issuer: String
        get() = "https://securetoken.google.com/$projectId"

    val signInProviders: List<String>
        get() = listOf("password", "google.com")
}
