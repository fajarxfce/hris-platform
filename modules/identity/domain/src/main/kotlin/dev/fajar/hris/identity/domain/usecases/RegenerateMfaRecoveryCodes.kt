package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.MfaRepository
import java.time.Clock

class RegenerateMfaRecoveryCodes(
    private val mfa: MfaRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val policy: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor): Result<MfaChallengeSuccess> {
        val startedAt = clock.instant()
        val recent = requireRecentMfa(actor, startedAt, policy.recentAuthenticationAge)
        if (recent is Result.Failed) return recent
        return transactions.run(actor.copy(companyId = null)) {
            val locked = mfa.lock(actor.accountId)
            if (locked is Result.Failed) return@run locked
            val credential = (locked as Result.Success).value
            val valid = validateMfaCredential(actor, credential)
            if (valid is Result.Failed) return@run valid
            val current = requireNotNull(credential)
            val now = clock.instant()
            val stillRecent = requireRecentMfa(actor, now, policy.recentAuthenticationAge)
            if (stillRecent is Result.Failed) return@run stillRecent
            if (!current.account.mfaConfigured)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "mfa_not_configured"))
            val attempt =
                mfa.takeAttempt(
                    actor.accountId,
                    mfaAttemptWindow(now, policy),
                    policy.maximumMfaAttempts,
                )
            if (attempt is Result.Failed) return@run attempt
            if (!(attempt as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.RATE_LIMITED, "mfa_rate_limited"))
            mfa.replaceRecoveryCodes(actor.accountId, now, policy.recoveryCodeCount).flatMap { codes
                ->
                mfa.advanceSecurityVersion(actor.accountId, current.account.securityVersion)
                    .flatMap { version ->
                        journal
                            .record(
                                actor,
                                ChangeRecord(
                                    "account",
                                    actor.accountId,
                                    "identity.mfa_recovery_regenerated",
                                ),
                            )
                            .map {
                                MfaChallengeSuccess(MfaProof(actor.accountId, version, now), codes)
                            }
                    }
            }
        }
    }
}
