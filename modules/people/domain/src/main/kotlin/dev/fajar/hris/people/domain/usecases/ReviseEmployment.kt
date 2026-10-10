package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.UnitKind
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class ReviseEmployment(
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val units: OrganizationRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val transfers: EmploymentTransferRepository,
    private val lifecycle: LifecycleRepository,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        terms: EmploymentTerms,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("people.manage")
        if (access is Result.Failed) return access
        val valid = validateEmployment(terms, reason)
        if (valid is Result.Failed) return valid
        if (version < 0) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "people.revise",
                operationId,
                listOf(
                    id.toString(),
                    version.toString(),
                    terms.effectiveFrom.toString(),
                    terms.contract.name,
                    terms.startDate.toString(),
                    terms.endDate?.toString(),
                    terms.status.name,
                    terms.branchId?.toString(),
                    terms.departmentId?.toString(),
                    terms.positionId?.toString(),
                    terms.costCenterId?.toString(),
                    terms.managerId?.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = people.lockReportingLines(company)
            if (lock is Result.Failed) return@run lock
            val unitLock = units.lockStructure(company)
            if (unitLock is Result.Failed) return@run unitLock
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
            val existing = people.find(company, id, terms.effectiveFrom)
            if (existing is Result.Failed) return@run existing
            val employee =
                (existing as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employee.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (terms.startDate != employee.terms.startDate)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "employment_start_immutable")
                )
            val closed = lifecycle.completedOffboardingDate(company, id)
            if (closed is Result.Failed) return@run closed
            val lastWorkingDate = (closed as Result.Success).value
            if (
                lastWorkingDate != null &&
                    terms.effectiveFrom.isAfter(lastWorkingDate) &&
                    (terms.status != EmploymentStatus.ENDED || terms.endDate != lastWorkingDate)
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "employment_offboarded"))
            val transferred = transfers.forEmployee(company, id)
            if (transferred is Result.Failed) return@run transferred
            val outgoing =
                (transferred as Result.Success).value.firstOrNull {
                    it.sourceCompanyId == company && it.sourceEmploymentId == id
                }
            if (
                outgoing != null &&
                    !terms.effectiveFrom.isBefore(outgoing.effectiveDate) &&
                    (terms.status != EmploymentStatus.ENDED ||
                        terms.endDate != outgoing.effectiveDate.minusDays(1))
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "transferred_employment_closed")
                )
            for ((unitId, kind) in
                listOf(
                    terms.branchId to UnitKind.BRANCH,
                    terms.departmentId to UnitKind.DEPARTMENT,
                    terms.positionId to UnitKind.POSITION,
                    terms.costCenterId to UnitKind.COST_CENTER,
                )) {
                if (unitId == null) continue
                val result = units.find(company, unitId)
                if (result is Result.Failed) return@run result
                val unit = (result as Result.Success).value
                if (unit == null || !unit.active || unit.kind != kind)
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "organization_assignment_unavailable")
                    )
            }
            if (terms.managerId != null) {
                val manager = people.find(company, terms.managerId, terms.effectiveFrom)
                if (manager is Result.Failed) return@run manager
                if (
                    (manager as Result.Success).value?.terms?.isWorkingOn(terms.effectiveFrom) !=
                        true
                )
                    return@run Result.Failed(Failure(FailureKind.VALIDATION, "manager_unavailable"))
            }
            val graph = people.reportingHistory(company, id, terms.managerId)
            if (graph is Result.Failed) return@run graph
            val graphValidation =
                validateReportingLine(
                    ReportingAssignment(id, terms.managerId, terms.effectiveFrom, version + 1),
                    (graph as Result.Success).value,
                )
            if (graphValidation is Result.Failed) return@run graphValidation
            people.revise(actor, id, version, terms, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "employment",
                                id,
                                "people.employment_revised",
                                mapOf(
                                    "effectiveFrom" to terms.effectiveFrom.toString(),
                                    "revision" to receipt.version.toString(),
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
