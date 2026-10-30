package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class GetLeaveRequest(
    private val requests: LeaveRequestRepository,
    private val ledger: LeaveLedgerRepository,
    private val people: PeopleRepository,
    private val approvals: ApprovalRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val cutoffs: PayrollCutoffRepository,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        historyAfter: Long?,
        historyLimit: Int,
        scope: LeaveRequestReadScope = LeaveRequestReadScope.AUTHORIZED,
    ): Result<LeaveRequestDetails> {
        val maximum = if (scope == LeaveRequestReadScope.SELF) 50 else 200
        if ((historyAfter ?: 0) < 0 || historyLimit !in 1..maximum)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val original = requests.find(company, id)
            if (original is Result.Failed) return@run original
            val employeeId =
                (original as Result.Success).value?.employeeId
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_request_not_found")
                    )
            val lock = ledger.lock(company, employeeId, shared = true)
            if (lock is Result.Failed) return@run lock
            val cutoffLock = cutoffs.lock(company)
            if (cutoffLock is Result.Failed) return@run cutoffLock
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val approvalGuard = approvals.lock(company, shared = true)
            if (approvalGuard is Result.Failed) return@run approvalGuard
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val admitted =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (admitted is Result.Failed) return@run admitted
            val found = requests.find(company, id)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_request_not_found")
                    )
            val initialResult = approvals.find(company, request.approvalId)
            if (initialResult is Result.Failed) return@run initialResult
            val initial =
                (initialResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "approval_unavailable")
                    )
            val cancellationId = request.cancellationApprovalId
            val cancellation =
                if (cancellationId == null) null
                else {
                    val result = approvals.find(company, cancellationId)
                    if (result is Result.Failed) return@run result
                    (result as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "approval_unavailable")
                        )
                }
            val now = clock.instant()
            val currentResult = people.findAtInstant(company, employeeId, now)
            if (currentResult is Result.Failed) return@run currentResult
            val current = (currentResult as Result.Success).value
            val active =
                if (request.status == LeaveStatus.CANCELLATION_PENDING) requireNotNull(cancellation)
                else initial
            val delegationResult = approvals.delegations(company, actor.accountId, now)
            if (delegationResult is Result.Failed) return@run delegationResult
            val delegations = (delegationResult as Result.Success).value
            val assigned = active.stages.flatMap { it.assignees }.toSet()
            val parties =
                (if (delegations.size <= 200) delegations else emptyList())
                    .filter {
                        it.kind == active.kind &&
                            it.toAccount == actor.accountId &&
                            it.fromAccount in assigned
                    }
                    .map { it.fromAccount }
                    .toSet() + actor.accountId
            for (party in parties.sorted()) {
                val accountGuard = identities.lockAccount(party, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (
                scope == LeaveRequestReadScope.SELF &&
                    !canReadOwnLeaveRequest(live, request, current)
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "leave_request_not_found"))
            val memberResult = members.candidates(company, parties, emptySet(), parties.size)
            if (memberResult is Result.Failed) return@run memberResult
            val grants = (memberResult as Result.Success).value
            if (
                !hasLeaveRequestReadScope(
                    live,
                    request,
                    current,
                    active,
                    delegations,
                    grants,
                    clock.instant(),
                )
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "leave_request_not_found"))
            val accountResult = people.accountForEmployee(company, employeeId)
            if (accountResult is Result.Failed) return@run accountResult
            val beneficiary = (accountResult as Result.Success).value
            val actions =
                leaveAvailableActions(
                        live,
                        request,
                        active,
                        delegations,
                        grants,
                        clock.instant(),
                        beneficiary,
                    )
                    .toMutableSet()
            if (LeaveAction.REQUEST_CANCELLATION in actions) {
                val frozen =
                    cutoffs.frozenMonths(
                        company,
                        employeeId,
                        request.days.map { java.time.YearMonth.from(it.workDate) }.toSet(),
                    )
                if (frozen is Result.Failed) return@run frozen
                if ((frozen as Result.Success).value)
                    actions.remove(LeaveAction.REQUEST_CANCELLATION)
                for (year in request.days.map { it.workDate.year }.distinct()) {
                    val balance = ledger.balance(company, employeeId, request.policy.typeId, year)
                    if (balance is Result.Failed) return@run balance
                    if ((balance as Result.Success).value.closed)
                        actions.remove(LeaveAction.REQUEST_CANCELLATION)
                }
            }
            requests.history(company, id, historyAfter, historyLimit).flatMap { history ->
                val completedAt = clock.instant()
                validateCompanySessionActor(actor, access, completedAt, security)
                    .flatMap completion@{ completed ->
                        if (
                            !hasLeaveRequestReadScope(
                                completed,
                                request,
                                current,
                                active,
                                delegations,
                                grants,
                                completedAt,
                            )
                        )
                            return@completion Result.Failed(
                                Failure(FailureKind.NOT_FOUND, "leave_request_not_found")
                            )
                        val available =
                            actions.intersect(
                                leaveAvailableActions(
                                    completed,
                                    request,
                                    active,
                                    delegations,
                                    grants,
                                    completedAt,
                                    beneficiary,
                                )
                            )
                        Result.Success(
                            LeaveRequestDetails(request, initial, cancellation, history, available)
                        )
                    }
            }
        }
    }
}
