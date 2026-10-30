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

class StartLifecycleCase(
    private val lifecycle: LifecycleRepository,
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val people: PeopleRepository,
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
        id: UUID,
        command: StartLifecycleCommand,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("people.lifecycle.manage")
        if (access is Result.Failed) return access
        if (command.assignees.size > 64)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_lifecycle_case"))
        val selected = command.copy(assignees = command.assignees.toMap())
        val valid = validateLifecycleDate(selected.targetDate, selected.reason)
        if (valid is Result.Failed) return valid
        if (selected.templateVersion < 0 || selected.assignees.size > 64)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_lifecycle_case"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "people.lifecycle_start",
                operationId,
                listOf(
                    id.toString(),
                    selected.employmentId.toString(),
                    selected.templateId.toString(),
                    selected.templateVersion.toString(),
                    selected.targetDate.toString(),
                    selected.reason,
                ) + selected.assignees.toSortedMap().flatMap { listOf(it.key, it.value.toString()) },
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val structure = people.lockReportingLines(company)
            if (structure is Result.Failed) return@run structure
            val templateLock = lifecycle.lockTemplates(company)
            if (templateLock is Result.Failed) return@run templateLock
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            for (account in (selected.assignees.values + actor.accountId).toSet().sorted()) {
                val accountGuard = identities.lockAccount(account, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val identityAccess = (authority as Result.Success).value
            val checked =
                validateCompanySessionActor(actor, identityAccess, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permission = live.requirePermission("people.lifecycle.manage")
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val previousClosure = lifecycle.completedOffboardingDate(company, selected.employmentId)
            if (previousClosure is Result.Failed) return@run previousClosure
            if ((previousClosure as Result.Success).value != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "employment_offboarded"))
            val found = lifecycle.template(company, selected.templateId)
            if (found is Result.Failed) return@run found
            val template = (found as Result.Success).value
            if (template == null || !template.active)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "lifecycle_template_unavailable")
                )
            if (template.version != selected.templateVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_template_version"))
            if (!template.tasks.map { it.key }.containsAll(selected.assignees.keys))
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_lifecycle_assignee")
                )
            val employment = people.find(company, selected.employmentId, selected.targetDate)
            if (employment is Result.Failed) return@run employment
            val employee =
                (employment as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (
                selected.targetDate < employee.terms.startDate ||
                    employee.terms.status == EmploymentStatus.ENDED
            )
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "employment_unavailable"))
            for (id in selected.assignees.values.toSet()) {
                val result = members.find(company, id)
                if (result is Result.Failed) return@run result
                val member = (result as Result.Success).value
                if (
                    member == null ||
                        !member.accountActive ||
                        !member.membershipActive ||
                        member.permissions.none {
                            it == "people.lifecycle.perform" || it == "people.lifecycle.manage"
                        }
                )
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "lifecycle_assignee_unavailable")
                    )
            }
            val case =
                LifecycleCase(
                    id,
                    selected.employmentId,
                    template.kind,
                    selected.targetDate,
                    template.id,
                    template.version,
                    template.name,
                    LifecycleStatus.OPEN,
                    0,
                    actor.accountId,
                    clock.instant(),
                    template.tasks.map {
                        LifecycleTask(
                            it.key,
                            it.title,
                            it.required,
                            selected.targetDate.plusDays(it.dueDays.toLong()),
                            selected.assignees[it.key],
                            LifecycleTaskStatus.PENDING,
                            null,
                            null,
                        )
                    },
                )
            lifecycle.create(actor, case, selected.reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "lifecycle_case",
                                id,
                                "people.lifecycle_started",
                                mapOf(
                                    "employmentId" to selected.employmentId.toString(),
                                    "kind" to template.kind.name,
                                ),
                                selected.reason,
                            ),
                        )
                    }
                    .flatMap {
                        validateCompanySessionActor(
                                actor,
                                identityAccess,
                                clock.instant(),
                                security,
                            )
                            .map { receipt }
                    }
            }
        }
    }
}
