package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.MfaRepository
import java.time.Clock
import java.util.UUID

class ConfirmMfaEnrollment(
    private val mfa: MfaRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val policy: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, operationId: UUID, code: String): Result<MfaChallengeSuccess> {
        if (code.length !in 1..64)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_mfa_request"))
        val startedAt = clock.instant()
        val recent = requireRecentAuthentication(actor, startedAt, policy.recentAuthenticationAge)
        if (recent is Result.Failed) return recent
        return transactions
            .run<MfaChallengeOutcome>(actor.copy(companyId = null)) {
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
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "mfa_already_configured")
                    )
                val pending = current.pending
                if (pending?.operationId != operationId || !pending.expiresAt.isAfter(now))
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "mfa_enrollment_expired")
                    )
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
                val matched = mfa.matchEnrollment(actor.accountId, code, now)
                if (matched is Result.Failed) return@run matched
                val counter = (matched as Result.Success).value
                if (counter == null)
                    return@run Result.Success(
                        MfaChallengeOutcome.Denied(
                            Failure(FailureKind.UNAUTHENTICATED, "invalid_mfa_code")
                        )
                    )
                mfa.activate(actor.accountId, operationId, current.account.securityVersion, counter)
                    .flatMap { securityVersion ->
                        mfa.replaceRecoveryCodes(actor.accountId, now, policy.recoveryCodeCount)
                            .flatMap { codes ->
                                journal
                                    .record(
                                        actor,
                                        ChangeRecord(
                                            "account",
                                            actor.accountId,
                                            "identity.mfa_enrolled",
                                        ),
                                    )
                                    .map {
                                        MfaChallengeOutcome.Accepted(
                                            MfaChallengeSuccess(
                                                MfaProof(actor.accountId, securityVersion, now),
                                                codes,
                                            )
                                        )
                                    }
                            }
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
