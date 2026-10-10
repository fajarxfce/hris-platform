package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class CancelEmploymentRevision(
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
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
        revision: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("people.manage")
        if (access is Result.Failed) return access
        if (expectedVersion < 0 || revision <= 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_revision_cancellation"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "people.revision_cancel",
                operationId,
                listOf(id.toString(), expectedVersion.toString(), revision.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = people.lockReportingLines(company)
            if (lock is Result.Failed) return@run lock
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                    .flatMap { it.requirePermission("people.manage") }
            if (checked is Result.Failed) return@run checked
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val version = people.currentVersion(company, id)
            if (version is Result.Failed) return@run version
            if ((version as Result.Success).value != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val found = people.findRevision(company, id, revision)
            if (found is Result.Failed) return@run found
            val current =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employment_revision_not_found")
                    )
            if (current.cancellation != null)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "revision_already_cancelled")
                )
            val owner = companies.find(company)
            if (owner is Result.Failed) return@run owner
            val timezone =
                (owner as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = LocalDate.now(clock.withZone(ZoneId.of(timezone)))
            if (!current.terms.effectiveFrom.isAfter(today))
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "effective_revision_cannot_be_cancelled")
                )
            people.cancelRevision(actor, id, expectedVersion, revision, reason).flatMap { receipt ->
                val graph = people.reportingHistory(company, id, null)
                if (graph is Result.Failed) return@flatMap graph
                val valid =
                    dev.fajar.hris.people.domain.policies.validateReportingHistory(
                        id,
                        current.terms.effectiveFrom,
                        (graph as Result.Success).value,
                    )
                if (valid is Result.Failed) return@flatMap valid
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "employment",
                                id,
                                "people.revision_cancelled",
                                mapOf("revision" to revision.toString()),
                                reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
