package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class LeaseIdentityMail(
    private val mail: IdentityMailRepository,
    private val credentials: CredentialChallengeRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val policy: CredentialChallengePolicy,
    private val clock: Clock,
) {
    fun execute(owner: UUID, limit: Int): Result<List<IdentityMailLease>> {
        if (limit !in 1..2)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_mail_capacity"))
        if (!policy.enabled) return Result.Success(emptyList())
        val actor = Actor(UUID(0, 0), null, emptySet(), clock.instant(), UUID.randomUUID())
        // Retention locks challenge rows before their dependent queue rows.
        val cleanup =
            transactions.run(actor) {
                credentials.purgeExpired(clock.instant().minusSeconds(7 * 86400), 100)
            }
        if (cleanup is Result.Failed) return cleanup
        return transactions.run(actor) {
            val now = clock.instant()
            val exhausted = mail.exhausted(now, policy.maximumDeliveryAttempts, 100)
            if (exhausted is Result.Failed) return@run exhausted
            for (delivery in (exhausted as Result.Success).value) {
                val code =
                    if (!now.isBefore(delivery.expiresAt)) "credential_link_expired"
                    else "mail_attempts_exhausted"
                val failed = mail.failUnleased(delivery.challengeId, now, code)
                if (failed is Result.Failed) return@run failed
                if ((failed as Result.Success).value) {
                    val recorded =
                        journal.record(
                            actor,
                            ChangeRecord(
                                "identity_mail",
                                delivery.challengeId,
                                "identity.mail_failed",
                                mapOf("code" to code),
                            ),
                        )
                    if (recorded is Result.Failed) return@run recorded
                }
            }
            mail.claim(owner, limit, policy.mailLeaseSeconds, policy.maximumDeliveryAttempts)
        }
    }
}
