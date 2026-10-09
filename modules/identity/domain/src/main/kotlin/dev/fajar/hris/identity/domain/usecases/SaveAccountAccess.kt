package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.ManagedAccount
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.AccountAdministrationRepository
import java.time.Clock
import java.util.UUID

class SaveAccountAccess(
    private val accounts: AccountAdministrationRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long,
        active: Boolean,
        permissions: Set<String>,
        reason: String,
    ): Result<MutationReceipt> {
        if (actor.companyId != null || "identity.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_administrator_required"))
        val recent = requireRecentIdentityAdministration(actor, clock.instant(), security)
        if (recent is Result.Failed) return recent
        if (
            expectedVersion < 0 ||
                !PermissionCatalog.platformAdministrator.containsAll(permissions) ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_account_access"))
        if (id == actor.accountId && (!active || "identity.manage" !in permissions))
            return Result.Failed(Failure(FailureKind.CONFLICT, "cannot_remove_own_administration"))
        val key =
            OperationKey(
                "identity.account_access_save",
                operationId,
                listOf(id.toString(), expectedVersion.toString(), active.toString(), reason) +
                    permissions.sorted(),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = accounts.lockAdministration()
            if (lock is Result.Failed) return@run lock
            val lockedAccounts = mutableMapOf<UUID, ManagedAccount?>()
            for (accountId in setOf(actor.accountId, id).sorted()) {
                val locked = accounts.lockAccount(accountId)
                if (locked is Result.Failed) return@run locked
                lockedAccounts[accountId] = (locked as Result.Success).value
            }
            val currentAuthor = lockedAccounts[actor.accountId]
            if (
                currentAuthor == null ||
                    !currentAuthor.account.active ||
                    "identity.manage" !in currentAuthor.platformPermissions ||
                    (actor.credentialVersion != null &&
                        currentAuthor.account.securityVersion != actor.credentialVersion)
            )
                return@run Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
            val currentAssurance =
                requireRecentIdentityAdministration(actor, clock.instant(), security)
            if (currentAssurance is Result.Failed) return@run currentAssurance
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val target =
                lockedAccounts[id]
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "account_not_found"))
            if (target.account.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (active && target.invitationPending)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "invitation_acceptance_required")
                )
            accounts.save(id, expectedVersion, active, permissions).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "account",
                                id,
                                "identity.account_access_saved",
                                mapOf(
                                    "active" to active.toString(),
                                    "permissions" to permissions.sorted().joinToString(","),
                                ),
                                reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
