package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.OidcIdentityRepository
import java.time.Clock
import java.util.UUID

/**
 * Called only after the OIDC transport has verified signature, issuer, audience, time, state and
 * nonce.
 */
class SignInWithOidc(
    private val links: OidcIdentityRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val policy: OidcPolicy,
    private val clock: Clock,
) {
    fun execute(issuer: String, subject: String, correlationId: UUID): Result<Account> {
        if (!policy.enabled || issuer != policy.issuer || subject.length !in 1..255)
            return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "sso_sign_in_failed"))
        val found = links.resolve(issuer, subject)
        if (found is Result.Failed) return found
        val link =
            (found as Result.Success).value
                ?: return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "sso_sign_in_failed"))
        val actor = Actor(link.accountId, null, emptySet(), clock.instant(), correlationId)
        return transactions.run(actor) {
            val locked = links.lockSubject(issuer, subject)
            if (locked is Result.Failed) return@run locked
            val latest = links.find(link.id)
            if (latest is Result.Failed) return@run latest
            val current = (latest as Result.Success).value
            if (current == null || !current.active || current.version != link.version)
                return@run Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "sso_sign_in_failed"))
            links.lockAccount(link.accountId).flatMap { account ->
                if (account == null || !account.active)
                    Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "sso_sign_in_failed"))
                else
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "account",
                                account.id,
                                "identity.oidc_verified",
                                mapOf("identityId" to link.id.toString()),
                            ),
                        )
                        .map { account }
            }
        }
    }
}
