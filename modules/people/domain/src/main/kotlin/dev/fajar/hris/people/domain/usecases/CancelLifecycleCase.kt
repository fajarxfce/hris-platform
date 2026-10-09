package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class CancelLifecycleCase(
    private val lifecycle: LifecycleRepository,
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val people: PeopleRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("people.lifecycle.manage")
        if (access is Result.Failed) return access
        if (expectedVersion < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_lifecycle_change"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "people.lifecycle_cancel",
                operationId,
                listOf(id.toString(), expectedVersion.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val structure = people.lockReportingLines(company)
            if (structure is Result.Failed) return@run structure
            val lock = lifecycle.lockCase(company, id)
            if (lock is Result.Failed) return@run lock
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permission = live.requirePermission("people.lifecycle.manage")
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val result = lifecycle.case(company, id)
            if (result is Result.Failed) return@run result
            val case =
                (result as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found")
                    )
            if (case.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (case.status != LifecycleStatus.OPEN)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_case_not_open"))
            val event =
                LifecycleEvent(
                    case.version + 1,
                    null,
                    LifecycleAction.CANCELLED,
                    null,
                    actor.accountId,
                    reason,
                    clock.instant(),
                )
            lifecycle.finish(actor, id, case.version, LifecycleStatus.CANCELLED, event).flatMap {
                receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "lifecycle_case",
                                id,
                                "people.lifecycle_cancelled",
                                reason = reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
