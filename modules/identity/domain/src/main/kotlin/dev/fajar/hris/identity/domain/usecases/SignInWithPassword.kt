package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.Account
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock
import java.util.UUID

class SignInWithPassword(
    private val identities: IdentityRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val limits:
        dev.fajar.hris.identity.domain.repositories.AuthenticationRateLimitRepository,
    private val enforceLimits: Boolean = true,
    private val policy: dev.fajar.hris.identity.domain.entities.AuthenticationAttemptPolicy =
        dev.fajar.hris.identity.domain.entities.AuthenticationAttemptPolicy(),
) {
    fun execute(
        email: String,
        password: String,
        correlationId: UUID,
        origin: String = "unspecified",
    ): Result<Account> {
        if (email.length > 254 || password.length !in 1..128) {
            return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "invalid_credentials"))
        }
        if (origin.isBlank() || origin.length > 128)
            return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "invalid_credentials"))
        if (enforceLimits) {
            val attempt =
                transactions.run(
                    Actor(UUID(0, 0), null, emptySet(), clock.instant(), correlationId)
                ) {
                    // Denied attempts are successful accounting outcomes and must commit.
                    val now = clock.instant()
                    limits.purgeExpired(now.minusSeconds(86400), 100).flatMap {
                        limits.takeAttempt(email.trim().lowercase(), origin, now, policy)
                    }
                }
            if (attempt is Result.Failed) return attempt
            if (!(attempt as Result.Success).value)
                return Result.Failed(Failure(FailureKind.RATE_LIMITED, "sign_in_rate_limited"))
        }
        return identities.verifyPassword(email.trim().lowercase(), password).flatMap { account ->
            if (account == null || !account.active) {
                Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "invalid_credentials"))
            } else {
                val actor = Actor(account.id, null, emptySet(), clock.instant(), correlationId)
                transactions.run(actor) {
                    identities.access(account.id, null).flatMap { current ->
                        if (
                            current == null ||
                                !current.account.active ||
                                current.account.version != account.version
                        ) {
                            Result.Failed(
                                Failure(FailureKind.UNAUTHENTICATED, "invalid_credentials")
                            )
                        } else {
                            journal
                                .record(
                                    actor,
                                    ChangeRecord(
                                        "account",
                                        account.id,
                                        "identity.password_verified",
                                    ),
                                )
                                .map { account }
                        }
                    }
                }
            }
        }
    }
}
