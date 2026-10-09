package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID

class DecidePayrollReview(
    private val reviews: PayrollReviewRepository,
    private val runs: PayrollRunRepository,
    private val policies: PayrollPolicyRepository,
    private val approvals: ApprovalRepository,
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
        expectedApprovalVersion: Long,
        decision: ApprovalDecision,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = actor.requirePermission("payroll.review")
        if (permission is Result.Failed) return permission
        if (expectedVersion !in 0..9 || expectedApprovalVersion < 0 || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_review"))
        val key =
            OperationKey(
                "payroll.review_decide",
                operationId,
                listOf(
                    id.toString(),
                    expectedVersion.toString(),
                    expectedApprovalVersion.toString(),
                    decision.name,
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = policies.lock(company)
            if (guard is Result.Failed) return@run guard
            val approvalGuard = approvals.lock(company)
            if (approvalGuard is Result.Failed) return@run approvalGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val found = reviews.find(company, id)
            if (found is Result.Failed) return@run found
            val review = (found as Result.Success).value
            val foundApproval =
                if (review == null) Result.Success(null)
                else approvals.find(company, review.approvalId)
            if (foundApproval is Result.Failed) return@run foundApproval
            val approval = (foundApproval as Result.Success).value
            val delegated = approvals.delegations(company, actor.accountId, clock.instant())
            if (delegated is Result.Failed) return@run delegated
            val delegations = (delegated as Result.Success).value
            if (delegations.size > 200)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "approval_delegation_capacity")
                )
            val assigned = approval?.stages?.flatMap { it.assignees }?.toSet() ?: emptySet()
            val parties =
                delegations
                    .filter { it.kind == ApprovalKind.PAYROLL && it.fromAccount in assigned }
                    .map { it.fromAccount }
                    .toSet() + actor.accountId
            for (party in parties.sorted()) {
                val locked = identities.lockAccount(party, shared = true)
                if (locked is Result.Failed) return@run locked
            }
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val access = requirePayrollMutation(live, "payroll.review", clock.instant(), security)
            if (access is Result.Failed) return@run access
            if (review == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_review_not_found"))
            if (approval == null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_unavailable"))
            if (
                actor.accountId == approval.authorId ||
                    actor.accountId in approval.excludedAccountIds
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "self_approval_denied"))
            val foundMembers = members.candidates(company, parties, emptySet(), parties.size)
            if (foundMembers is Result.Failed) return@run foundMembers
            val grants = (foundMembers as Result.Success).value
            val now = clock.instant()
            if (!isAssignedApprover(live, approval, delegations, grants, now))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "not_assigned_approver"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (review.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (approval.version != expectedApprovalVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            if (review.status != PayrollReviewStatus.PENDING)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_review_not_pending")
                )
            val foundRun = runs.find(company, review.runId)
            if (foundRun is Result.Failed) return@run foundRun
            val run = (foundRun as Result.Success).value
            if (run?.status != PayrollRunStatus.CALCULATED || run.version != review.runVersion)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_review_not_current")
                )
            val planned = decideApproval(approval, live, decision, reason, delegations, grants, now)
            if (planned is Result.Failed) return@run planned
            val transition = (planned as Result.Success).value
            val change =
                PayrollReviewChange(
                    review.version + 1,
                    if (decision == ApprovalDecision.APPROVE) PayrollReviewAction.APPROVED
                    else PayrollReviewAction.REJECTED,
                    payrollReviewStatus(transition.status),
                    approval.version + 1,
                    approval.currentStep,
                    actor.accountId,
                    transition.decidingFor,
                    reason,
                    now,
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
                .flatMap { reviews.transition(company, review, change) }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "payroll_review",
                                id,
                                "payroll.review_decided",
                                mapOf(
                                    "runId" to run.id.toString(),
                                    "decision" to decision.name,
                                    "status" to change.status.name,
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
