package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.validateAudienceGroupInput
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class SaveAudienceGroup(
    private val groups: AudienceGroupRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        request: SaveAudienceGroupCommand,
    ): Result<MutationReceipt> {
        val allowed = actor.requirePermission("announcements.manage")
        if (allowed is Result.Failed) return allowed
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val input =
            request.copy(
                name = request.name.trim(),
                employmentIds = request.employmentIds.toList(),
                reason = request.reason.trim(),
            )
        val valid = validateAudienceGroupInput(input)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "communications.group_save",
                operationId,
                listOf(
                    input.id.toString(),
                    input.expectedVersion?.toString(),
                    input.name,
                    input.active.toString(),
                    input.reason,
                ) + input.employmentIds.map { it.toString() }.sorted(),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val groupGuard = groups.lock(company)
            if (groupGuard is Result.Failed) return@run groupGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authorized =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                    .flatMap { it.requirePermission("announcements.manage") }
            if (authorized is Result.Failed) return@run authorized
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = groups.find(company, input.id)
            if (found is Result.Failed) return@run found
            val previous = (found as Result.Success).value
            if (previous?.version != input.expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (previous != null && previous.version >= 999)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "audience_group_revision_limit")
                )
            if (previous == null) {
                val count = groups.count(company)
                if (count is Result.Failed) return@run count
                if ((count as Result.Success).value >= 128)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "audience_group_limit"))
            }
            val selected = input.employmentIds.toSet()
            val available = people.existingEmployeeIds(company, selected)
            if (available is Result.Failed) return@run available
            if ((available as Result.Success).value != selected)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "audience_group_employee_unavailable")
                )
            val snapshot =
                AudienceGroup(
                    input.id,
                    (previous?.version ?: -1L) + 1,
                    input.name,
                    input.active,
                    selected,
                    clock.instant(),
                    actor.accountId,
                    input.reason,
                )
            groups.save(company, snapshot, input.expectedVersion).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "audience_group",
                                input.id,
                                "communications.group_saved",
                                mapOf("version" to receipt.version.toString()),
                                input.reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
