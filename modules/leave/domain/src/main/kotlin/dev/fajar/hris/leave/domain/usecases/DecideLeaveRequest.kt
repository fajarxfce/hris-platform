package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class DecideLeaveRequest(
    private val requests: LeaveRequestRepository,
    private val ledger: LeaveLedgerRepository,
    private val approvals: ApprovalRepository,
    private val identities: IdentityRepository,
    private val companies: CompanyRepository,
    private val people: PeopleRepository,
    private val members: MembershipRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        decision: ApprovalDecision,
        reason: String,
    ): Result<MutationReceipt> {
        if (actor.permissions.none { it in approvalPermissions(ApprovalKind.LEAVE) })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (version < 0 || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_decision"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "leave.request_decide",
                operationId,
                listOf(id.toString(), version.toString(), decision.name, reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val original = requests.find(company, id)
            if (original is Result.Failed) return@run original
            val employeeId =
                (original as Result.Success).value?.employeeId
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_request_not_found")
                    )
            val lock = ledger.lock(company, employeeId)
            if (lock is Result.Failed) return@run lock
            val peopleLock = people.lockReportingLines(company)
            if (peopleLock is Result.Failed) return@run peopleLock
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val found = requests.find(company, id)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_request_not_found")
                    )
            val approvalId =
                if (request.status == LeaveStatus.CANCELLATION_PENDING)
                    requireNotNull(request.cancellationApprovalId)
                else request.approvalId
            val approvalResult = approvals.find(company, approvalId)
            if (approvalResult is Result.Failed) return@run approvalResult
            val approval =
                (approvalResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "approval_unavailable")
                    )
            val assignees = approval.stages[approval.currentStep].assignees
            for (accountId in (assignees + actor.accountId).sorted()) {
                val locked = identities.lockAccount(accountId)
                if (locked is Result.Failed) return@run locked
            }
            val checkedActor =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checkedActor is Result.Failed) return@run checkedActor
            val live = (checkedActor as Result.Success).value
            if (live.permissions.none { it in approvalPermissions(ApprovalKind.LEAVE) })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (request.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (request.status !in setOf(LeaveStatus.PENDING, LeaveStatus.CANCELLATION_PENDING))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_not_pending"))
            val now = clock.instant()
            val accountResult = people.accountForEmployee(company, employeeId)
            if (accountResult is Result.Failed) return@run accountResult
            val beneficiary = (accountResult as Result.Success).value
            val delegationResult = approvals.delegations(company, actor.accountId, now)
            if (delegationResult is Result.Failed) return@run delegationResult
            val memberResult = members.candidates(company, assignees, emptySet(), assignees.size)
            if (memberResult is Result.Failed) return@run memberResult
            val transitionResult =
                decideApproval(
                    approval,
                    live,
                    decision,
                    reason,
                    (delegationResult as Result.Success).value,
                    (memberResult as Result.Success).value,
                    now,
                )
            if (transitionResult is Result.Failed) return@run transitionResult
            val transition = (transitionResult as Result.Success).value
            if (
                beneficiary != null && beneficiary in setOf(actor.accountId, transition.decidingFor)
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "self_approval_denied"))
            val outcomeResult = leaveDecisionOutcome(request.status, transition.status)
            if (outcomeResult is Result.Failed) return@run outcomeResult
            val outcome = (outcomeResult as Result.Success).value
            val movements = outcome.effect?.let { leaveLedgerMovements(request.days, it) }.orEmpty()
            for (movement in movements) {
                val balance =
                    ledger.balance(company, employeeId, request.policy.typeId, movement.year)
                if (balance is Result.Failed) return@run balance
                val valid = validateLeaveMovement((balance as Result.Success).value, movement)
                if (valid is Result.Failed) return@run valid
            }
            val entries =
                movements.map {
                    LeaveLedgerEntry(
                        UUID.randomUUID(),
                        employeeId,
                        request.policy.typeId,
                        it.year,
                        it.kind,
                        id,
                        id,
                        it.availableDelta,
                        it.reservedDelta,
                        it.consumedDelta,
                        actor.accountId,
                        now,
                        reason,
                    )
                }
            approvals
                .decide(
                    actor,
                    approvalId,
                    approval.version,
                    approval.currentStep,
                    transition,
                    reason,
                    now,
                )
                .flatMap {
                    requests.update(
                        actor,
                        id,
                        version,
                        outcome.status,
                        request.cancellationApprovalId,
                        LeaveChangeKind.DECIDED,
                        reason,
                        now,
                    )
                }
                .flatMap { receipt ->
                    ledger
                        .append(company, entries)
                        .flatMap {
                            if (
                                outcome.status in setOf(LeaveStatus.REJECTED, LeaveStatus.CANCELLED)
                            )
                                requests.releaseOccupancy(
                                    company,
                                    id,
                                    request.days.sumOf { it.portion.halfDays },
                                )
                            else Result.Success(Unit)
                        }
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "leave_request",
                                    id,
                                    "leave.request_decided",
                                    mapOf(
                                        "approvalId" to approvalId.toString(),
                                        "decision" to decision.name,
                                        "status" to outcome.status.name,
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
