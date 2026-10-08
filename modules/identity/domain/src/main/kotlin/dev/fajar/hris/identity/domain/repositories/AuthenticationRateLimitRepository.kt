package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.Result
import java.time.Instant

interface AuthenticationRateLimitRepository {
    fun takeAttempt(
        email: String,
        origin: String,
        at: Instant,
        policy: dev.fajar.hris.identity.domain.entities.AuthenticationAttemptPolicy,
        kind: dev.fajar.hris.identity.domain.entities.AuthenticationAttemptKind =
            dev.fajar.hris.identity.domain.entities.AuthenticationAttemptKind.SIGN_IN,
    ): Result<Boolean>

    fun purgeExpired(before: Instant, limit: Int): Result<Int>
}
