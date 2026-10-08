package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class ConfirmAccountCredential(
    private val credentials: CredentialChallengeRepository,
    private val mail: IdentityMailRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        kind: CredentialChallengeKind,
        token: String,
        password: String,
        correlationId: UUID,
    ): Result<Unit> {
        if (token.length != 43 || token.any { !it.isLetterOrDigit() && it != '_' && it != '-' })
            return Result.Failed(Failure(FailureKind.VALIDATION, "credential_link_unavailable"))
        val valid = validateNewPassword(password)
        if (valid is Result.Failed) return valid
        // The hash lookup establishes the account scope; the locked transaction rechecks every
        // condition.
        val found = credentials.findByToken(token)
        if (found is Result.Failed) return found
        val candidate =
            (found as Result.Success).value
                ?: return Result.Failed(
                    Failure(FailureKind.VALIDATION, "credential_link_unavailable")
                )
        val actor = Actor(candidate.accountId, null, emptySet(), clock.instant(), correlationId)
        return transactions.run(actor) {
            val locked = credentials.lockAccount(candidate.accountId)
            if (locked is Result.Failed) return@run locked
            val account = (locked as Result.Success).value
            val current = credentials.find(candidate.id)
            if (current is Result.Failed) return@run current
            val challenge = (current as Result.Success).value
            val now = clock.instant()
            if (
                account == null ||
                    challenge == null ||
                    challenge.kind != kind ||
                    !credentialChallengeUsable(challenge, account, now)
            )
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "credential_link_unavailable")
                )
            val saved = credentials.setPassword(account.id, account.version, password, true)
            if (saved is Result.Failed) return@run saved
            val acceptedAt = clock.instant()
            if (!credentialChallengeUsable(challenge, account, acceptedAt))
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "credential_link_unavailable")
                )
            credentials
                .consume(challenge.id, acceptedAt)
                .flatMap { credentials.revokePending(account.id, acceptedAt) }
                .flatMap { mail.supersedePending(account.id, acceptedAt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "account",
                            account.id,
                            if (kind == CredentialChallengeKind.INVITATION)
                                "identity.invitation_accepted"
                            else "identity.password_recovered",
                            mapOf("challengeId" to challenge.id.toString()),
                        ),
                    )
                }
        }
    }
}
