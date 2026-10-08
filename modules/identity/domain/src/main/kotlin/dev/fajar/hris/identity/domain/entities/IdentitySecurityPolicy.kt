package dev.fajar.hris.identity.domain.entities

import java.time.Duration

data class IdentitySecurityPolicy(
    val enforceMfa: Boolean = true,
    val maximumMfaAge: Duration = Duration.ofHours(12),
    val recentAuthenticationAge: Duration = Duration.ofMinutes(10),
    val enrollmentLifetime: Duration = Duration.ofMinutes(10),
    val mfaAttemptWindowSeconds: Long = 300,
    val maximumMfaAttempts: Int = 5,
    val recoveryCodeCount: Int = 10,
) {
    init {
        require(maximumMfaAge > Duration.ZERO && maximumMfaAge <= Duration.ofDays(1))
        require(recentAuthenticationAge > Duration.ZERO && recentAuthenticationAge <= maximumMfaAge)
        require(enrollmentLifetime > Duration.ZERO && enrollmentLifetime <= Duration.ofHours(1))
        require(mfaAttemptWindowSeconds in 1..3600 && maximumMfaAttempts in 1..100)
        require(recoveryCodeCount in 1..20)
    }
}
