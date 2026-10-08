package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.Result
import java.time.Instant

interface SignInLimitRepository {
    fun takeAttempt(
        email: String,
        origin: String,
        at: Instant,
        policy: dev.fajar.hris.identity.domain.entities.SignInAttemptPolicy,
    ): Result<Boolean>

    fun purgeExpired(before: Instant, limit: Int): Result<Int>
}
