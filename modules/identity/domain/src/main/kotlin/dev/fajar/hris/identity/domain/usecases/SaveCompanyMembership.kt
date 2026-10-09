package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
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
    private val roles: RoleTemplateRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        accountId: UUID,
        expectedVersion: Long?,
        active: Boolean,
        permissions: Set<String>,
        reason: String,
        roleTemplates: List<RoleTemplateSelection> = emptyList(),
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
                roleTemplates.size > 8 ||
                roleTemplates.map { it.id }.distinct().size != roleTemplates.size ||
                roleTemplates.any { it.version < 0 } ||
                reason.isBlank() ||
                reason.length > 1000 ||
                (expectedVersion ?: 0) < 0
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_membership"))
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
                ) +
                    permissions.sorted() +
                    roleTemplates
                        .sortedBy { it.id }
                        .flatMap { listOf("template", it.id.toString(), it.version.toString()) },
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (roleTemplates.isNotEmpty()) {
                val roleLock = roles.lock(company)
                if (roleLock is Result.Failed) return@run roleLock
            }
            val applied = mutableListOf<AppliedRoleTemplate>()
            for (selection in roleTemplates.sortedBy { it.id }) {
                val found = roles.find(company, selection.id)
                if (found is Result.Failed) return@run found
                val role = (found as Result.Success).value
                if (role == null || !role.active)
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "role_template_unavailable")
                    )
                if (role.version != selection.version)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_role_template"))
                applied +=
                    AppliedRoleTemplate(
                        role.id,
                        role.code,
                        role.name,
                        role.version,
                        role.permissions,
                    )
            }
            val effectivePermissions = permissions + applied.flatMap { it.permissions }
            if (!PermissionCatalog.assignable.containsAll(effectivePermissions))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_membership"))
            if (
                accountId == actor.accountId &&
                    (!active || "identity.manage" !in effectivePermissions)
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "cannot_remove_own_administration")
                )
            val lock = members.lock(company)
            if (lock is Result.Failed) return@run lock
            val existing = members.find(company, accountId)
            if (existing is Result.Failed) return@run existing
            val member = (existing as Result.Success).value
            val previousGrants =
                if (member?.membershipActive == true) member.permissions else emptySet()
            val added = effectivePermissions - previousGrants
            if (
                added.any {
                    (it.startsWith("payroll.") && it != "payroll.self.read") ||
                        it == "expenses.pay" ||
                        it == "documents.retention"
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
                    (!active || "identity.manage" !in effectivePermissions)
            ) {
                val administrators =
                    members.hasOtherActiveMember(company, accountId, "identity.manage")
                if (administrators is Result.Failed) return@run administrators
                if (!(administrators as Result.Success).value)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "last_company_administrator")
                    )
            }
            val target = identities.access(accountId, null)
            if (target is Result.Failed) return@run target
            if ((target as Result.Success).value?.account?.active != true)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "account_unavailable"))
            members
                .save(company, accountId, expectedVersion, active, effectivePermissions)
                .flatMap { receipt ->
                    roles
                        .recordApplication(
                            actor,
                            accountId,
                            receipt.version,
                            MembershipGrant(permissions.toSet(), applied.toList()),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "company_membership",
                                    accountId,
                                    "identity.membership_saved",
                                    mapOf(
                                        "permissions" to
                                            effectivePermissions.sorted().joinToString(","),
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
