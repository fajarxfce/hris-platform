package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class GetOvertimeRequest(
    private val overtime: OvertimeRepository,
    private val periods: WorkPeriodRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val approvals: ApprovalRepository,
) {
    fun execute(actor: Actor, id: UUID, after: Long?, limit: Int): Result<OvertimeRequestDetails> {
        if ((after ?: 0) < 0 || limit !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val original = overtime.find(company, id)
            if (original is Result.Failed) return@run original
            val employeeId =
                (original as Result.Success).value?.employeeId
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "overtime_request_not_found")
                    )
            val lock = overtime.lock(company, employeeId, shared = true)
            if (lock is Result.Failed) return@run lock
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
            val found = overtime.find(company, id)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "overtime_request_not_found")
                    )
            val approval =
                if (request.approvalId == null) null
                else {
                    val result = approvals.find(company, request.approvalId)
                    if (result is Result.Failed) return@run result
                    (result as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "approval_unavailable")
                        )
                }
            val assigned = approval?.stages?.flatMap { it.assignees }?.toSet().orEmpty()
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            for (accountId in (assigned + actor.accountId).sorted()) {
                val accountLock = identities.lockAccount(accountId, shared = true)
                if (accountLock is Result.Failed) return@run accountLock
            }
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val now = clock.instant()
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val currentResult = people.find(company, employeeId, today)
            if (currentResult is Result.Failed) return@run currentResult
            val current = (currentResult as Result.Success).value
            val delegated = approvals.delegations(company, actor.accountId, now)
            if (delegated is Result.Failed) return@run delegated
            val delegations = (delegated as Result.Success).value
            val candidates =
                if (assigned.isEmpty()) Result.Success(emptyList())
                else members.candidates(company, assigned, emptySet(), assigned.size)
            if (candidates is Result.Failed) return@run candidates
            val grants = (candidates as Result.Success).value
            if (
                !canReadOvertime(live, current) &&
                    (approval == null ||
                        !isAssignedApprover(live, approval, delegations, grants, now))
            )
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "overtime_request_not_found")
                )
            val month = periods.find(company, YearMonth.from(request.workDate))
            if (month is Result.Failed) return@run month
            val mutable = (month as Result.Success).value?.let { !workPeriodLocked(it) } ?: true
            val actions =
                overtimeActions(
                    live,
                    request,
                    canManageOvertime(live, current, today),
                    mutable,
                    approval,
                    delegations,
                    grants,
                    current?.person?.accountId,
                    now,
                )
            overtime.history(company, id, after, limit).map {
                OvertimeRequestDetails(request, approval, it, actions)
            }
        }
    }
}
