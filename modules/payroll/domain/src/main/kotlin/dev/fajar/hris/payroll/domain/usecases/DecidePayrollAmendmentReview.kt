package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.decideApproval
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class DecidePayrollAmendmentReview(
    private val amendments: PayrollAmendmentRepository,
    private val reviews: PayrollAmendmentReviewRepository,
    private val sources: PayrollAmendmentSourceRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val approvals: ApprovalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        amendmentId: UUID,
        id: UUID,
        expectedVersion: Long,
        expectedReviewVersion: Long,
        expectedApprovalVersion: Long,
        decision: ApprovalDecision,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission =
            requirePayrollMutation(actor, "payroll.amend.review", clock.instant(), security)
        if (permission is Result.Failed) return permission
        val valid = validatePayrollAmendmentCommand(expectedVersion, reason)
        if (valid is Result.Failed) return valid
        if (expectedReviewVersion !in 0..9 || expectedApprovalVersion !in 0 until Long.MAX_VALUE)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "payroll.amendment_review_decide",
                operationId,
                listOf(
                    amendmentId.toString(),
                    id.toString(),
                    expectedVersion.toString(),
                    expectedReviewVersion.toString(),
                    expectedApprovalVersion.toString(),
                    decision.name,
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val payrollGuard = policies.lock(company)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val approvalGuard = approvals.lock(company)
            if (approvalGuard is Result.Failed) return@run approvalGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val found = reviews.find(company, id)
            if (found is Result.Failed) return@run found
            val review = (found as Result.Success).value?.takeIf { it.amendmentId == amendmentId }
            val foundApproval =
                review?.let { approvals.find(company, it.approvalId) } ?: Result.Success(null)
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
                    .filter {
                        it.kind == ApprovalKind.PAYROLL_AMENDMENT && it.fromAccount in assigned
                    }
                    .map { it.fromAccount }
                    .toSet() + actor.accountId
            for (party in parties.sorted()) {
                val accountGuard = identities.lockAccount(party, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed =
                requirePayrollMutation(live, "payroll.amend.review", clock.instant(), security)
            if (allowed is Result.Failed) return@run allowed
            if (review == null)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "payroll_amendment_review_not_found")
                )
            if (approval == null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_unavailable"))
            val foundAmendment = amendments.find(company, amendmentId)
            if (foundAmendment is Result.Failed) return@run foundAmendment
            val current =
                (foundAmendment as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_not_found")
                    )
            val owner = people.accountForEmployee(company, current.summary.employee.id)
            if (owner is Result.Failed) return@run owner
            val scoped = payrollAmendmentDecisionScope(approval, (owner as Result.Success).value)
            val candidates = members.candidates(company, parties, emptySet(), parties.size)
            if (candidates is Result.Failed) return@run candidates
            val grants = (candidates as Result.Success).value
            val authority =
                requirePayrollAmendmentDecisionActor(
                    live,
                    scoped,
                    delegations,
                    grants,
                    clock.instant(),
                    security,
                )
            if (authority is Result.Failed) return@run authority
            (replay as Result.Success).value?.let { receipt ->
                return@run validateCompanySessionActor(actor, access, clock.instant(), security)
                    .flatMap {
                        requirePayrollAmendmentDecisionActor(
                                it,
                                scoped,
                                delegations,
                                grants,
                                clock.instant(),
                                security,
                            )
                            .map { receipt }
                    }
            }
            if (
                current.summary.version != expectedVersion ||
                    review.version != expectedReviewVersion
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (approval.version != expectedApprovalVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            if (review.status != PayrollAmendmentReviewStatus.PENDING)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_review_not_pending")
                )
            if (
                current.summary.status != PayrollAmendmentStatus.IN_REVIEW ||
                    current.summary.latestReviewId != id ||
                    current.summary.latestCalculationId != review.calculationId
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_review_not_current")
                )
            if (decision == ApprovalDecision.APPROVE) {
                val acquired = sources.sources(company, current.summary.firstAssessmentId)
                if (acquired is Result.Failed) return@run acquired
                val basis =
                    preparePayrollAmendmentCorrections(
                        current.summary.firstAssessmentId,
                        (acquired as Result.Success).value,
                        current.terms.sourceVersions,
                        current.terms.corrections,
                    )
                if (basis is Result.Failed) return@run basis
            }
            val now = clock.instant()
            val planned = decideApproval(scoped, live, decision, reason, delegations, grants, now)
            if (planned is Result.Failed) return@run planned
            val transition = (planned as Result.Success).value
            val status = payrollAmendmentReviewStatus(transition.status)
            val action =
                if (decision == ApprovalDecision.APPROVE) PayrollAmendmentAction.APPROVED
                else PayrollAmendmentAction.REJECTED
            val change =
                PayrollAmendmentReviewChange(
                    review.version + 1,
                    expectedVersion + 1,
                    action,
                    status,
                    approval.version + 1,
                    approval.currentStep,
                    actor.accountId,
                    transition.decidingFor,
                    reason,
                    now,
                )
            val outcome =
                current.copy(
                    summary =
                        current.summary.copy(
                            version = expectedVersion + 1,
                            action = action,
                            status =
                                when (status) {
                                    PayrollAmendmentReviewStatus.APPROVED ->
                                        PayrollAmendmentStatus.APPROVED
                                    PayrollAmendmentReviewStatus.REJECTED ->
                                        PayrollAmendmentStatus.REJECTED
                                    else -> PayrollAmendmentStatus.IN_REVIEW
                                },
                            actorId = actor.accountId,
                            recordedAt = now,
                            reason = reason,
                        )
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
                .flatMap { amendments.save(company, outcome, expectedVersion) }
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "payroll_amendment",
                                amendmentId,
                                "payroll.amendment_review_decided",
                                mapOf(
                                    "reviewId" to id.toString(),
                                    "decision" to decision.name,
                                    "status" to status.name,
                                ),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            validateCompanySessionActor(actor, access, clock.instant(), security)
                        }
                        .flatMap {
                            requirePayrollAmendmentDecisionActor(
                                    it,
                                    scoped,
                                    delegations,
                                    grants,
                                    clock.instant(),
                                    security,
                                )
                                .map { receipt }
                        }
                }
        }
    }
}
