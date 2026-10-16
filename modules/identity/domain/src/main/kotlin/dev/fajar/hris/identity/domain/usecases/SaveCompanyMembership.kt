package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
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
            val recent = requireRecentMfa(actor, clock.instant(), security.recentAuthenticationAge)
            if (recent is Result.Failed) return recent
        }
        val selectedPermissions = permissions.toSet()
        val selectedRoles = roleTemplates.toList()
        if (
            !PermissionCatalog.assignable.containsAll(selectedPermissions) ||
                selectedRoles.size > 8 ||
                selectedRoles.map { it.id }.distinct().size != selectedRoles.size ||
                selectedRoles.any { it.version < 0 } ||
                reason.isBlank() ||
                reason.length > 1000 ||
                (expectedVersion ?: 0) < 0
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_membership"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
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
                    selectedPermissions.sorted() +
                    selectedRoles
                        .sortedBy { it.id }
                        .flatMap {
                            listOf(
                                if (it.retainApplied) "retained-template" else "template",
                                it.id.toString(),
                                it.version.toString(),
                            )
                        },
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            if (selectedRoles.isNotEmpty()) {
                val roleLock = roles.lock(company, shared = true)
                if (roleLock is Result.Failed) return@run roleLock
            }
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company)
            if (memberGuard is Result.Failed) return@run memberGuard
            for (id in setOf(actor.accountId, accountId).sorted()) {
                val accountGuard = identities.lockAccount(id, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val accessed = identities.access(actor.accountId, company)
            if (accessed is Result.Failed) return@run accessed
            val currentAccess = (accessed as Result.Success).value
            val checked =
                validateCompanySessionActor(actor, currentAccess, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permission = live.requirePermission("identity.manage")
            if (permission is Result.Failed) return@run permission
            if (security.enforceMfa) {
                val currentAssurance =
                    requireRecentMfa(actor, clock.instant(), security.recentAuthenticationAge)
                if (currentAssurance is Result.Failed) return@run currentAssurance
            }
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val existing = members.find(company, accountId)
            if (existing is Result.Failed) return@run existing
            val member = (existing as Result.Success).value
            if (member?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val retained =
                if (selectedRoles.any { it.retainApplied }) {
                    if (member == null || (active && !member.membershipActive))
                        return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "role_template_review_required")
                        )
                    val grant = roles.application(company, accountId, member.version)
                    if (grant is Result.Failed) return@run grant
                    (grant as Result.Success).value?.roleTemplates.orEmpty()
                } else emptyList()
            val applied = mutableListOf<AppliedRoleTemplate>()
            for (selection in selectedRoles.sortedBy { it.id }) {
                if (selection.retainApplied) {
                    val previous = retained.find { it.id == selection.id }
                    if (previous == null || previous.version != selection.version)
                        return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "stale_role_template")
                        )
                    applied += previous
                    continue
                }
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
            val effectivePermissions = selectedPermissions + applied.flatMap { it.permissions }
            if (!PermissionCatalog.assignable.containsAll(effectivePermissions))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_membership"))
            if (
                accountId == actor.accountId &&
                    (!active || "identity.manage" !in effectivePermissions)
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "cannot_remove_own_administration")
                )
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
                if ("identity.manage" !in live.platformPermissions)
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
            val targetAccount = (target as Result.Success).value?.account
            if (targetAccount == null || (active && !targetAccount.active))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "account_unavailable"))
            members
                .save(company, accountId, expectedVersion, active, effectivePermissions)
                .flatMap { receipt ->
                    roles
                        .recordApplication(
                            actor,
                            accountId,
                            receipt.version,
                            MembershipGrant(selectedPermissions, applied.toList()),
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
                        .flatMap {
                            validateCompanySessionActor(
                                actor,
                                currentAccess,
                                clock.instant(),
                                security,
                            )
                        }
                        .flatMap {
                            if (security.enforceMfa)
                                requireRecentMfa(
                                    actor,
                                    clock.instant(),
                                    security.recentAuthenticationAge,
                                )
                            else Result.Success(Unit)
                        }
                        .map { receipt }
                }
        }
    }
}
