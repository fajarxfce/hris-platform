package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.RoleTemplateRepository
import java.time.Clock
import java.util.UUID

class SaveRoleTemplate(
    private val roles: RoleTemplateRepository,
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
        expectedVersion: Long?,
        code: String,
        name: String,
        permissions: Set<String>,
        active: Boolean,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("identity.manage")
        if (access is Result.Failed) return access
        if (security.enforceMfa) {
            val recent = requireRecentMfa(actor, clock.instant(), security.recentAuthenticationAge)
            if (recent is Result.Failed) return recent
        }
        val normalizedCode = code.trim().uppercase()
        val normalizedName = name.trim()
        if (
            !normalizedCode.matches(Regex("[A-Z0-9][A-Z0-9_-]{1,31}")) ||
                normalizedName.isBlank() ||
                normalizedName.length > 200 ||
                !PermissionCatalog.assignable.containsAll(permissions) ||
                (expectedVersion ?: 0) < 0 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_role_template"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "identity.role_template_save",
                operationId,
                listOf(
                    id.toString(),
                    expectedVersion?.toString(),
                    normalizedCode,
                    normalizedName,
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
            val locked = roles.lock(company)
            if (locked is Result.Failed) return@run locked
            val found = roles.find(company, id)
            if (found is Result.Failed) return@run found
            val previous = (found as Result.Success).value
            if (previous?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (previous != null && previous.code != normalizedCode)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "role_code_immutable"))
            if (previous == null) {
                val count = roles.count(company)
                if (count is Result.Failed) return@run count
                if ((count as Result.Success).value >= 128)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "role_template_limit"))
            }
            val template =
                CompanyRoleTemplate(
                    id,
                    company,
                    normalizedCode,
                    normalizedName,
                    permissions.toSet(),
                    active,
                    previous?.version ?: 0,
                )
            roles.save(actor, template, expectedVersion, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "role_template",
                                id,
                                "identity.role_template_saved",
                                mapOf(
                                    "version" to receipt.version.toString(),
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
