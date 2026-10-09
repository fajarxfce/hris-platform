package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
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
) {
    fun execute(
        actor: Actor,
        id: UUID,
        historyAfter: Long?,
        historyLimit: Int,
    ): Result<LeaveRequestDetails> {
        if ((historyAfter ?: 0) < 0 || historyLimit !in 1..200)
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
            val lock = ledger.lock(company, employeeId)
            if (lock is Result.Failed) return@run lock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
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
            val scoped = canReadLeaveRequest(live, request, current)
            val active =
                if (request.status == LeaveStatus.CANCELLATION_PENDING) requireNotNull(cancellation)
                else initial
            val delegationResult = approvals.delegations(company, actor.accountId, now)
            if (delegationResult is Result.Failed) return@run delegationResult
            val assigned = active.stages.flatMap { it.assignees }.toSet()
            val memberResult = members.candidates(company, assigned, emptySet(), assigned.size)
            if (memberResult is Result.Failed) return@run memberResult
            val delegations = (delegationResult as Result.Success).value
            val grants = (memberResult as Result.Success).value
            if (!scoped && !isAssignedApprover(live, active, delegations, grants, now))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "leave_request_not_found"))
            val accountResult = people.accountForEmployee(company, employeeId)
            if (accountResult is Result.Failed) return@run accountResult
            val beneficiary = (accountResult as Result.Success).value
            val actions =
                leaveAvailableActions(live, request, active, delegations, grants, now, beneficiary)
            requests.history(company, id, historyAfter, historyLimit).map {
                LeaveRequestDetails(request, initial, cancellation, it, actions)
            }
        }
    }
}
