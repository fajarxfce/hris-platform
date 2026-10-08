package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.OidcIdentityRepository
import java.time.Clock
import java.util.UUID

class SaveOidcIdentity(
    private val links: OidcIdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val oidc: OidcPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        accountId: UUID,
        id: UUID,
        issuer: String,
        subject: String,
        active: Boolean,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        if (actor.companyId != null || "identity.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_administrator_required"))
        val recent =
            if (security.enforceMfa)
                requireRecentMfa(actor, clock.instant(), security.recentAuthenticationAge)
            else
                requireRecentAuthentication(
                    actor,
                    clock.instant(),
                    security.recentAuthenticationAge,
                )
        if (recent is Result.Failed) return recent
        if (
            issuer.length !in 1..500 ||
                subject.length !in 1..255 ||
                subject.any(Char::isISOControl) ||
                (expectedVersion ?: 0) < 0 ||
                reason.isBlank() ||
                reason.length > 1000 ||
                (active && (!oidc.enabled || issuer != oidc.issuer))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_oidc_identity"))
        val key =
            OperationKey(
                "identity.oidc_save",
                operationId,
                listOf(
                    accountId.toString(),
                    id.toString(),
                    issuer,
                    subject,
                    active.toString(),
                    expectedVersion?.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val locked = links.lockSubject(issuer, subject)
            if (locked is Result.Failed) return@run locked
            val found = links.lockAccount(accountId)
            if (found is Result.Failed) return@run found
            val account =
                (found as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "account_not_found"))
            if (active && !account.active)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "account_disabled"))
            val previous = links.find(id)
            if (previous is Result.Failed) return@run previous
            val current = (previous as Result.Success).value
            if (current?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (
                current != null &&
                    (current.accountId != accountId ||
                        current.issuer != issuer ||
                        current.subject != subject)
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "oidc_binding_immutable"))
            if (active && current?.active != true) {
                val count = links.activeCount(accountId)
                if (count is Result.Failed) return@run count
                if ((count as Result.Success).value >= 8)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "oidc_identity_limit"))
            }
            val identity =
                current?.copy(active = active)
                    ?: OidcIdentity(
                        id,
                        accountId,
                        issuer,
                        subject,
                        active,
                        0,
                        clock.instant(),
                        actor.accountId,
                    )
            links.save(identity, expectedVersion).flatMap { receipt ->
                links
                    .advanceSecurityVersion(accountId)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "oidc_identity",
                                id,
                                "identity.oidc_identity_changed",
                                mapOf(
                                    "accountId" to accountId.toString(),
                                    "active" to active.toString(),
                                ),
                                reason,
                            ),
                        )
                    }
                    .flatMap { operations.record(actor, key, receipt) }
                    .map { receipt }
            }
        }
    }
}
