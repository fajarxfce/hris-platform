package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class SaveLifecycleTemplate(
    private val lifecycle: LifecycleRepository,
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        template: LifecycleTemplate,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("people.lifecycle.manage")
        if (access is Result.Failed) return access
        val normalized =
            template.copy(
                code = template.code.trim().uppercase(),
                name = template.name.trim(),
                version = expectedVersion ?: 0,
            )
        val valid = validateLifecycleTemplate(normalized, reason)
        if (valid is Result.Failed) return valid
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "people.lifecycle_template_save",
                operationId,
                listOf(
                    template.id.toString(),
                    normalized.code,
                    normalized.name,
                    template.kind.name,
                    template.active.toString(),
                    expectedVersion?.toString(),
                    reason,
                ) +
                    template.tasks.flatMap {
                        listOf(it.key, it.title, it.required.toString(), it.dueDays.toString())
                    },
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = lifecycle.lockTemplates(company)
            if (lock is Result.Failed) return@run lock
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permission = live.requirePermission("people.lifecycle.manage")
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val previous = lifecycle.template(company, template.id)
            if (previous is Result.Failed) return@run previous
            val existing = (previous as Result.Success).value
            if (expectedVersion == null) {
                if (existing != null)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "lifecycle_template_exists")
                    )
                val count = lifecycle.templateCount(company)
                if (count is Result.Failed) return@run count
                if ((count as Result.Success).value >= 128)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "lifecycle_template_limit")
                    )
            } else {
                if (existing == null)
                    return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_template_not_found")
                    )
                if (existing.version != expectedVersion)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                if (existing.code != normalized.code || existing.kind != template.kind)
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "lifecycle_template_identity_immutable")
                    )
            }
            lifecycle.saveTemplate(actor, normalized, expectedVersion, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "lifecycle_template",
                                template.id,
                                "people.lifecycle_template_saved",
                                reason = reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
