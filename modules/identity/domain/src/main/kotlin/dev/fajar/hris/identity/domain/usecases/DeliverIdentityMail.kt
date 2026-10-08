package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.mail.domain.repositories.MailRepository
import java.time.Clock
import java.util.UUID

class DeliverIdentityMail(
    private val deliveries: IdentityMailRepository,
    private val credentials: CredentialChallengeRepository,
    private val mail: MailRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val policy: CredentialChallengePolicy,
    private val clock: Clock,
) {
    fun execute(lease: IdentityMailLease): Result<Unit> {
        val actor = Actor(UUID(0, 0), null, emptySet(), clock.instant(), UUID.randomUUID())
        val prepared =
            transactions.run<PreparedCredentialMail>(actor) {
                // Account-first ordering also serializes password changes and explicit invitation
                // resends.
                val locked = credentials.lockAccount(lease.accountId)
                if (locked is Result.Failed) return@run locked
                val account = (locked as Result.Success).value
                val owned = deliveries.lock(lease)
                if (owned is Result.Failed) return@run owned
                if ((owned as Result.Success).value == null)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "mail_lease_lost"))
                val found = credentials.find(lease.challengeId)
                if (found is Result.Failed) return@run found
                val challenge = (found as Result.Success).value
                val now = clock.instant()
                if (
                    account == null ||
                        challenge == null ||
                        !credentialChallengeUsable(challenge, account, now)
                ) {
                    return@run deliveries
                        .finish(
                            lease,
                            IdentityMailOutcome(
                                IdentityMailState.SUPERSEDED,
                                now,
                                "credential_link_unavailable",
                                true,
                            ),
                            now,
                        )
                        .flatMap { changed ->
                            if (!changed)
                                Result.Failed(Failure(FailureKind.CONFLICT, "mail_lease_lost"))
                            else
                                journal
                                    .record(
                                        actor,
                                        ChangeRecord(
                                            "identity_mail",
                                            lease.challengeId,
                                            "identity.mail_superseded",
                                        ),
                                    )
                                    .map { PreparedCredentialMail.Skipped }
                        }
                }
                deliveries.token(lease).map {
                    PreparedCredentialMail.Ready(
                        credentialMail(challenge, account, it, policy),
                        challenge,
                    )
                }
            }
        if (prepared is Result.Success && prepared.value == PreparedCredentialMail.Skipped)
            return Result.Success(Unit)
        if (prepared is Result.Failed && prepared.failure.code == "mail_lease_lost") return prepared
        // SMTP runs without a database transaction. Delivery is at least once with a stable
        // Message-ID.
        val sent =
            when (prepared) {
                is Result.Failed -> prepared
                is Result.Success ->
                    mail.send((prepared.value as PreparedCredentialMail.Ready).message)
            }
        return transactions.run(actor) {
            val now = clock.instant()
            val outcome =
                identityMailOutcome(
                    lease.attempts,
                    (sent as? Result.Failed)?.failure,
                    now,
                    lease.expiresAt,
                    policy.maximumDeliveryAttempts,
                )
            deliveries.finish(lease, outcome, now).flatMap { changed ->
                if (!changed) Result.Failed(Failure(FailureKind.CONFLICT, "mail_lease_lost"))
                else
                    journal.record(
                        actor,
                        ChangeRecord(
                            "identity_mail",
                            lease.challengeId,
                            "identity.mail_${outcome.state.name.lowercase()}",
                            mapOf(
                                "attempt" to lease.attempts.toString(),
                                "code" to (outcome.failureCode ?: "delivered"),
                            ),
                        ),
                    )
            }
        }
    }
}
