package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.MfaRepository
import java.time.Clock
import java.util.UUID

class BeginMfaEnrollment(
    private val mfa: MfaRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val policy: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, operationId: UUID): Result<MfaEnrollment> {
        val startedAt = clock.instant()
        val recent = requireRecentAuthentication(actor, startedAt, policy.recentAuthenticationAge)
        if (recent is Result.Failed) return recent
        if (!mfa.available())
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "identity_key_unavailable"))
        return transactions.run(actor.copy(companyId = null)) {
            val locked = mfa.lock(actor.accountId)
            if (locked is Result.Failed) return@run locked
            val credential = (locked as Result.Success).value
            val valid = validateMfaCredential(actor, credential)
            if (valid is Result.Failed) return@run valid
            val current = requireNotNull(credential)
            val now = clock.instant()
            val stillRecent =
                requireRecentAuthentication(actor, now, policy.recentAuthenticationAge)
            if (stillRecent is Result.Failed) return@run stillRecent
            if (current.account.mfaConfigured)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "mfa_already_configured"))
            val pending = current.pending
            if (pending?.operationId == operationId && pending.expiresAt.isAfter(now)) {
                return@run mfa.enrollment(current.account).flatMap {
                    if (it == null)
                        Result.Failed(Failure(FailureKind.CONFLICT, "mfa_enrollment_changed"))
                    else Result.Success(it)
                }
            }
            val attempt =
                mfa.takeAttempt(
                    actor.accountId,
                    mfaAttemptWindow(now, policy),
                    policy.maximumMfaAttempts,
                )
            if (attempt is Result.Failed) return@run attempt
            if (!(attempt as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.RATE_LIMITED, "mfa_rate_limited"))
            mfa.startEnrollment(current.account, operationId, now.plus(policy.enrollmentLifetime))
        }
    }
}
