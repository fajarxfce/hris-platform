package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.SavedReportFilterRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

class DeleteReportFilter(
    private val filters: SavedReportFilterRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        operationId: UUID,
        expectedVersion: Long,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permitted = actor.requirePermission("reports.read")
        if (permitted is Result.Failed) return permitted
        if (expectedVersion !in 0..10000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "reporting.filter_deleted",
                operationId,
                listOf(id.toString(), expectedVersion.toString()),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val ownerGuard = filters.lock(company, actor.accountId)
            if (ownerGuard is Result.Failed) return@run ownerGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val loadedAccess = identities.access(actor.accountId, company)
            if (loadedAccess is Result.Failed) return@run loadedAccess
            val snapshot = (loadedAccess as Result.Success).value
            val access =
                validateCompanySessionActor(actor, snapshot, clock.instant(), security).flatMap {
                    it.requirePermission("reports.read")
                }
            if (access is Result.Failed) return@run access
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = filters.find(company, actor.accountId, id)
            if (found is Result.Failed) return@run found
            val acquired = validateCompanySessionActor(actor, snapshot, clock.instant(), security)
            if (acquired is Result.Failed) return@run acquired
            val previous =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "report_filter_not_found")
                    )
            if (previous.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (previous.definition == null) {
                val receipt = MutationReceipt(id, previous.version)
                return@run operations.record(actor, key, receipt).flatMap {
                    validateCompanySessionActor(actor, snapshot, clock.instant(), security).map {
                        receipt
                    }
                }
            }
            if (previous.version >= 10000)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "report_filter_version_limit")
                )
            val updated =
                previous.copy(
                    version = previous.version + 1,
                    definition = null,
                    updatedAt =
                        maxOf(clock.instant().truncatedTo(ChronoUnit.MICROS), previous.updatedAt),
                )
            val stillAllowed =
                validateCompanySessionActor(actor, snapshot, clock.instant(), security)
            if (stillAllowed is Result.Failed) return@run stillAllowed
            filters.save(updated, previous.version).flatMap { saved ->
                if (!saved) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                else {
                    val receipt = MutationReceipt(id, updated.version)
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "report_filter",
                                    id,
                                    "reporting.filter_deleted",
                                    mapOf(
                                        "kind" to previous.definition.kind.name,
                                        "version" to updated.version.toString(),
                                    ),
                                ),
                            )
                        }
                        .flatMap {
                            validateCompanySessionActor(actor, snapshot, clock.instant(), security)
                                .map { receipt }
                        }
                }
            }
        }
    }
}
