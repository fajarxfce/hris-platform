package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.datasources.AuthenticationAttemptDataSource
import dev.fajar.hris.identity.domain.repositories.AuthenticationRateLimitRepository
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

class PostgresAuthenticationRateLimitRepository(
    private val attempts: AuthenticationAttemptDataSource
) : AuthenticationRateLimitRepository {
    override fun takeAttempt(
        email: String,
        origin: String,
        at: Instant,
        policy: dev.fajar.hris.identity.domain.entities.AuthenticationAttemptPolicy,
        kind: dev.fajar.hris.identity.domain.entities.AuthenticationAttemptKind,
    ): Result<Boolean> = safeDatabaseCall {
        val namespace =
            when (kind) {
                dev.fajar.hris.identity.domain.entities.AuthenticationAttemptKind.SIGN_IN -> ""
                dev.fajar.hris.identity.domain.entities.AuthenticationAttemptKind
                    .PASSWORD_RECOVERY -> "password-recovery:"
            }
        val start =
            Instant.ofEpochSecond(
                Math.floorDiv(at.epochSecond, policy.windowSeconds) * policy.windowSeconds
            )
        for ((label, value, maximum) in
            listOf(
                Triple("origin", origin, policy.perOrigin),
                Triple("account", email, policy.perAccount),
            )) {
            val key =
                HexFormat.of()
                    .formatHex(
                        MessageDigest.getInstance("SHA-256")
                            .digest("$namespace$label:$value".toByteArray(Charsets.UTF_8))
                    )
            if (!attempts.increment(key, start, start.plusSeconds(policy.windowSeconds), maximum))
                return@safeDatabaseCall false
        }
        true
    }

    override fun purgeExpired(before: Instant, limit: Int): Result<Int> = safeDatabaseCall {
        attempts.purgeExpired(before, limit)
    }
}
