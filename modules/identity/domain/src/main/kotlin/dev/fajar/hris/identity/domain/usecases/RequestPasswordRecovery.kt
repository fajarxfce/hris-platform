package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class RequestPasswordRecovery(
    private val credentials: CredentialChallengeRepository,
    private val mail: IdentityMailRepository,
    private val limits: AuthenticationRateLimitRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val policy: CredentialChallengePolicy,
    private val clock: Clock,
) {
    fun execute(email: String, origin: String, correlationId: UUID): Result<Unit> {
        if (email.length > 254 || origin.isBlank() || origin.length > 128)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_recovery_request"))
        val normalized = email.trim().lowercase()
        if (!policy.enabled || !validAccountEmail(normalized)) return Result.Success(Unit)
        val actor = Actor(UUID(0, 0), null, emptySet(), clock.instant(), correlationId)
        // Accounting commits independently, including requests with no eligible account.
        val permitted =
            transactions.run(actor) {
                val now = clock.instant()
                limits.purgeExpired(now.minusSeconds(86400), 100).flatMap {
                    limits.takeAttempt(
                        normalized,
                        origin,
                        now,
                        policy.attempts,
                        AuthenticationAttemptKind.PASSWORD_RECOVERY,
                    )
                }
            }
        if (permitted is Result.Failed) return permitted
        if (!(permitted as Result.Success).value) return Result.Success(Unit)
        return transactions.run(actor) {
            val found = credentials.findAccountByEmail(normalized)
            if (found is Result.Failed) return@run found
            val candidate = (found as Result.Success).value ?: return@run Result.Success(Unit)
            val locked = credentials.lockAccount(candidate.id)
            if (locked is Result.Failed) return@run locked
            val account = (locked as Result.Success).value ?: return@run Result.Success(Unit)
            if (!account.active || account.invitationPending || !account.hasPassword)
                return@run Result.Success(Unit)
            val now = clock.instant()
            val pending = credentials.pending(account.id, CredentialChallengeKind.PASSWORD_RECOVERY)
            if (pending is Result.Failed) return@run pending
            val previous = (pending as Result.Success).value
            if (previous != null && credentialChallengeUsable(previous, account, now)) {
                val delivery = mail.find(previous.id)
                if (delivery is Result.Failed) return@run delivery
                val state = (delivery as Result.Success).value?.state
                if (
                    state != null &&
                        state != IdentityMailState.FAILED &&
                        state != IdentityMailState.SUPERSEDED
                )
                    return@run Result.Success(Unit)
            }
            val challenge =
                CredentialChallenge(
                    UUID.randomUUID(),
                    account.id,
                    CredentialChallengeKind.PASSWORD_RECOVERY,
                    account.securityVersion,
                    actor.accountId,
                    now,
                    now.plusSeconds(policy.recoverySeconds),
                    null,
                    null,
                )
            credentials
                .revokePending(account.id, now)
                .flatMap { mail.supersedePending(account.id, now) }
                .flatMap { credentials.issue(challenge) }
                .flatMap { mail.enqueue(it) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "account",
                            account.id,
                            "identity.password_recovery_requested",
                            mapOf("challengeId" to challenge.id.toString()),
                        ),
                    )
                }
        }
    }
}
