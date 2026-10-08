package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.MfaRepository
import java.time.Clock

class VerifyMfa(
    private val mfa: MfaRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val policy: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, code: String, recovery: Boolean): Result<MfaChallengeSuccess> {
        if (code.length !in 1..64)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_mfa_request"))
        val normalized = code.replace("-", "").replace(" ", "").uppercase()
        return transactions
            .run<MfaChallengeOutcome>(actor.copy(companyId = null)) {
                val locked = mfa.lock(actor.accountId)
                if (locked is Result.Failed) return@run locked
                val credential = (locked as Result.Success).value
                val valid = validateMfaCredential(actor, credential)
                if (valid is Result.Failed) return@run valid
                val current = requireNotNull(credential)
                val now = clock.instant()
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
                    return@run Result.Success(
                        MfaChallengeOutcome.Denied(
                            Failure(FailureKind.RATE_LIMITED, "mfa_rate_limited")
                        )
                    )
                val accepted: Boolean
                if (recovery) {
                    val result = mfa.consumeRecovery(actor.accountId, normalized, now)
                    if (result is Result.Failed) return@run result
                    accepted = (result as Result.Success).value
                } else {
                    val result = mfa.matchAuthenticator(actor.accountId, normalized, now)
                    if (result is Result.Failed) return@run result
                    val counter = (result as Result.Success).value
                    if (counter == null || counter <= (current.lastCounter ?: -1))
                        return@run Result.Success(
                            MfaChallengeOutcome.Denied(
                                Failure(FailureKind.UNAUTHENTICATED, "invalid_mfa_code")
                            )
                        )
                    val recorded =
                        mfa.recordCounter(actor.accountId, current.account.securityVersion, counter)
                    if (recorded is Result.Failed) return@run recorded
                    accepted = (recorded as Result.Success).value
                }
                if (!accepted)
                    return@run Result.Success(
                        MfaChallengeOutcome.Denied(
                            Failure(FailureKind.UNAUTHENTICATED, "invalid_mfa_code")
                        )
                    )
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "account",
                            actor.accountId,
                            if (recovery) "identity.mfa_recovery_used" else "identity.mfa_verified",
                        ),
                    )
                    .map {
                        MfaChallengeOutcome.Accepted(
                            MfaChallengeSuccess(
                                MfaProof(actor.accountId, current.account.securityVersion, now)
                            )
                        )
                    }
            }
            .flatMap {
                when (it) {
                    is MfaChallengeOutcome.Accepted -> Result.Success(it.result)
                    is MfaChallengeOutcome.Denied -> Result.Failed(it.failure)
                }
            }
    }
}
