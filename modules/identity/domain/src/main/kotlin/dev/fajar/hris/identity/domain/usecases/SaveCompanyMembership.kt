package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.identity.domain.repositories.*
import java.util.UUID

class SaveCompanyMembership(
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: java.time.Clock,
    private val security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        accountId: UUID,
        expectedVersion: Long?,
        active: Boolean,
        permissions: Set<String>,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("identity.manage")
        if (access is Result.Failed) return access
        if (security.enforceMfa) {
            val recent =
                dev.fajar.hris.identity.domain.policies.requireRecentMfa(
                    actor,
                    clock.instant(),
                    security.recentAuthenticationAge,
                )
            if (recent is Result.Failed) return recent
        }
        if (
            !PermissionCatalog.assignable.containsAll(permissions) ||
                reason.isBlank() ||
                reason.length > 1000 ||
                (expectedVersion ?: 0) < 0
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_membership"))
        if (accountId == actor.accountId && (!active || "identity.manage" !in permissions))
            return Result.Failed(Failure(FailureKind.CONFLICT, "cannot_remove_own_administration"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "identity.membership_save",
                operationId,
                listOf(
                    accountId.toString(),
                    expectedVersion?.toString(),
                    active.toString(),
                    reason,
                ) + permissions.sorted(),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val lock = members.lock(company)
            if (lock is Result.Failed) return@run lock
            val existing = members.find(company, accountId)
            if (existing is Result.Failed) return@run existing
            val member = (existing as Result.Success).value
            val added = permissions - member?.permissions.orEmpty()
            if (
                added.any {
                    (it.startsWith("payroll.") && it != "payroll.self.read") || it == "expenses.pay"
                }
            ) {
                if (accountId == actor.accountId)
                    return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "cannot_self_grant_sensitive_access")
                    )
                val global = identities.access(actor.accountId, null)
                if (global is Result.Failed) return@run global
                if ("identity.manage" !in (global as Result.Success).value?.permissions.orEmpty())
                    return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "platform_administrator_required")
                    )
            }
            if (
                member != null &&
                    member.membershipActive &&
                    "identity.manage" in member.permissions &&
                    (!active || "identity.manage" !in permissions)
            ) {
                val administrators =
                    members.candidates(company, emptySet(), setOf("identity.manage"), 200)
                if (administrators is Result.Failed) return@run administrators
                if (
                    (administrators as Result.Success).value.count {
                        it.accountActive && it.membershipActive
                    } <= 1
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "last_company_administrator")
                    )
            }
            val target = identities.access(accountId, null)
            if (target is Result.Failed) return@run target
            if ((target as Result.Success).value?.account?.active != true)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "account_unavailable"))
            members.save(company, accountId, expectedVersion, active, permissions).flatMap { receipt
                ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "company_membership",
                                accountId,
                                "identity.membership_saved",
                                mapOf(
                                    "permissions" to permissions.sorted().joinToString(","),
                                    "active" to active.toString(),
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
