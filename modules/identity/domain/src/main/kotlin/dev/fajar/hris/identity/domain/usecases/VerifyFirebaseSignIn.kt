package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.validateFirebaseIdentity
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID
import java.util.concurrent.CancellationException

/** A verified external subject must still pass approved HRIS identity linking and session gates. */
class VerifyFirebaseSignIn(
    private val identities: FirebaseIdentityRepository,
    private val limits: AuthenticationRateLimitRepository,
    private val transactions: TransactionRunner,
    private val policy: FirebaseSignInPolicy,
    private val clock: Clock,
    private val attempts: AuthenticationAttemptPolicy = AuthenticationAttemptPolicy(),
) {
    fun execute(idToken: String, origin: String, correlationId: UUID): Result<FirebaseIdentity> {
        active()
        if (!policy.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "firebase_sign_in_unavailable"))
        if (idToken.length !in 32..8192 || origin.isBlank() || origin.length > 128)
            return rejected()
        val admitted =
            takeAttempt(
                origin,
                origin,
                correlationId,
                AuthenticationAttemptKind.FIREBASE_VERIFICATION,
                attempts.copy(perAccount = attempts.perOrigin),
            )
        if (admitted is Result.Failed) return admitted
        active()
        val verified = identities.verify(idToken)
        active()
        if (verified is Result.Failed) return verified
        val checked =
            validateFirebaseIdentity((verified as Result.Success).value, policy, clock.instant())
        if (checked is Result.Failed) return checked
        val identity = (checked as Result.Success).value
        val accepted =
            takeAttempt(
                "${policy.projectId}:${identity.subject}",
                origin,
                correlationId,
                AuthenticationAttemptKind.FIREBASE_SIGN_IN,
                attempts,
            )
        if (accepted is Result.Failed) return accepted
        active()
        return validateFirebaseIdentity(identity, policy, clock.instant())
    }

    private fun takeAttempt(
        subject: String,
        origin: String,
        correlationId: UUID,
        kind: AuthenticationAttemptKind,
        limit: AuthenticationAttemptPolicy,
    ): Result<Unit> {
        val result =
            transactions.run(Actor(UUID(0, 0), null, emptySet(), clock.instant(), correlationId)) {
                active()
                val now = clock.instant()
                limits.purgeExpired(now.minusSeconds(86400), 100).flatMap {
                    limits.takeAttempt(subject, origin, now, limit, kind)
                }
            }
        active()
        return result.flatMap { allowed ->
            if (allowed) Result.Success(Unit)
            else Result.Failed(Failure(FailureKind.RATE_LIMITED, "sign_in_rate_limited"))
        }
    }

    private fun active() {
        if (Thread.currentThread().isInterrupted)
            throw CancellationException("Firebase sign-in cancelled")
    }

    private fun rejected() =
        Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "firebase_sign_in_failed"))
}
