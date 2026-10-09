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

class DecideOvertimeRequest(
    private val overtime: OvertimeRepository,
    private val periods: WorkPeriodRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val approvals: ApprovalRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        decision: ApprovalDecision,
        reason: String,
    ): Result<MutationReceipt> {
        if (actor.permissions.none { it in approvalPermissions(ApprovalKind.OVERTIME) })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (version !in 0..998 || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_overtime_decision"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "overtime.decide",
                operationId,
                listOf(id.toString(), version.toString(), decision.name, reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val originalResult = overtime.find(company, id)
            if (originalResult is Result.Failed) return@run originalResult
            val original =
                (originalResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "overtime_request_not_found")
                    )
            val employeeId = original.employeeId
            val periodResult = periods.lockMonth(company, YearMonth.from(original.workDate), false)
            if (periodResult is Result.Failed) return@run periodResult
            val period = (periodResult as Result.Success).value
            val lock = overtime.lock(company, employeeId)
            if (lock is Result.Failed) return@run lock
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val found = overtime.find(company, id)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "overtime_request_not_found")
                    )
            val approvalId =
                request.approvalId
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "overtime_not_pending")
                    )
            val approvalResult = approvals.find(company, approvalId)
            if (approvalResult is Result.Failed) return@run approvalResult
            val approval =
                (approvalResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "approval_unavailable")
                    )
            val assignees = approval.stages[approval.currentStep].assignees
            for (accountId in (assignees + actor.accountId).sorted()) {
                val accountLock = identities.lockAccount(accountId, shared = true)
                if (accountLock is Result.Failed) return@run accountLock
            }
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (live.permissions.none { it in approvalPermissions(ApprovalKind.OVERTIME) })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (request.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (request.status != OvertimeStatus.PENDING)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "overtime_not_pending"))
            val mutable = requireMutablePeriod(period)
            if (mutable is Result.Failed) return@run mutable
            val now = clock.instant()
            val beneficiaryResult = people.accountForEmployee(company, employeeId)
            if (beneficiaryResult is Result.Failed) return@run beneficiaryResult
            val beneficiary = (beneficiaryResult as Result.Success).value
            val delegations = approvals.delegations(company, actor.accountId, now)
            if (delegations is Result.Failed) return@run delegations
            val candidates = members.candidates(company, assignees, emptySet(), assignees.size)
            if (candidates is Result.Failed) return@run candidates
            val decided =
                decideApproval(
                    approval,
                    live,
                    decision,
                    reason,
                    (delegations as Result.Success).value,
                    (candidates as Result.Success).value,
                    now,
                )
            if (decided is Result.Failed) return@run decided
            val transition = (decided as Result.Success).value
            val independent =
                independentOvertimeDecision(
                    request,
                    actor.accountId,
                    transition.decidingFor,
                    beneficiary,
                )
            if (independent is Result.Failed) return@run independent
            val status =
                when (transition.status) {
                    ApprovalStatus.APPROVED -> OvertimeStatus.APPROVED
                    ApprovalStatus.REJECTED -> OvertimeStatus.REJECTED
                    else -> OvertimeStatus.PENDING
                }
            val updated =
                request.copy(
                    status = status,
                    approvedMinutes =
                        if (status == OvertimeStatus.APPROVED)
                            requireNotNull(request.actual).workedMinutes
                        else 0,
                    decidedAt = if (status == OvertimeStatus.PENDING) null else now,
                )
            approvals
                .decide(
                    actor,
                    approval.id,
                    approval.version,
                    approval.currentStep,
                    transition,
                    reason,
                    now,
                )
                .flatMap {
                    overtime.update(actor, updated, OvertimeChangeKind.DECIDED, reason, now)
                }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "overtime_request",
                                id,
                                "overtime.decided",
                                mapOf(
                                    "decision" to decision.name,
                                    "status" to status.name,
                                    "approvalId" to approval.id.toString(),
                                ),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .map { receipt }
                }
        }
    }
}
