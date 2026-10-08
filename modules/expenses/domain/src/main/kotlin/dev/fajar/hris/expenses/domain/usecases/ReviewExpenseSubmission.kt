package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.approvals.domain.entities.ApprovalKind
import dev.fajar.hris.approvals.domain.policies.approvalPermissions
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.ExpenseClaimRepository
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class ReviewExpenseSubmission(
    private val claims: ExpenseClaimRepository,
    private val approvals: ApprovalRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
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
        approvalVersion: Long,
        decision: ExpenseDecision,
        reason: String,
        acknowledgeDuplicates: Boolean,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (actor.permissions.none { it in approvalPermissions(ApprovalKind.EXPENSE) })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (version < 0 || approvalVersion < 0 || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_expense_decision"))
        val key =
            OperationKey(
                "expenses.submission_review",
                operationId,
                listOf(
                    id.toString(),
                    version.toString(),
                    approvalVersion.toString(),
                    decision.name,
                    reason,
                    acknowledgeDuplicates.toString(),
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val expenseLock = claims.lock(company)
            if (expenseLock is Result.Failed) return@run expenseLock
            val peopleLock = people.lockReportingLines(company)
            if (peopleLock is Result.Failed) return@run peopleLock
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val membershipLock = members.lock(company)
            if (membershipLock is Result.Failed) return@run membershipLock
            val foundSubmission = claims.submission(company, id)
            if (foundSubmission is Result.Failed) return@run foundSubmission
            val submission =
                (foundSubmission as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_submission_not_found")
                    )
            val foundClaim = claims.find(company, submission.claimId)
            if (foundClaim is Result.Failed) return@run foundClaim
            val claim =
                (foundClaim as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_claim_not_found")
                    )
            val foundApproval = approvals.find(company, submission.approvalId)
            if (foundApproval is Result.Failed) return@run foundApproval
            val approval =
                (foundApproval as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "approval_unavailable")
                    )
            val foundDelegations = approvals.delegations(company, actor.accountId, clock.instant())
            if (foundDelegations is Result.Failed) return@run foundDelegations
            val delegations = (foundDelegations as Result.Success).value
            if (delegations.size > 200)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "approval_delegation_capacity")
                )
            val assigned = approval.stages.flatMap { it.assignees }.toSet()
            val parties =
                delegations
                    .filter {
                        it.kind == ApprovalKind.EXPENSE &&
                            it.toAccount == actor.accountId &&
                            it.fromAccount in assigned
                    }
                    .map { it.fromAccount }
                    .toSet() + actor.accountId
            for (party in parties.sorted()) {
                val locked = identities.lockAccount(party)
                if (locked is Result.Failed) return@run locked
            }
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (live.permissions.none { it in approvalPermissions(ApprovalKind.EXPENSE) })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val beneficiaryResult = people.accountForEmployee(company, claim.employmentId)
            if (beneficiaryResult is Result.Failed) return@run beneficiaryResult
            val beneficiary = (beneficiaryResult as Result.Success).value
            if (actor.accountId in excludedExpenseReviewers(submission, beneficiary))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "self_approval_denied"))
            val grantsResult = members.candidates(company, parties, emptySet(), parties.size)
            if (grantsResult is Result.Failed) return@run grantsResult
            val grants = (grantsResult as Result.Success).value
            val now = clock.instant()
            if (
                !hasExpenseReviewScope(
                    submission,
                    approval,
                    live,
                    beneficiary,
                    delegations,
                    grants,
                    now,
                )
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "not_assigned_approver"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (claim.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (approval.version != approvalVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
            val digests = submission.lines.flatMap { it.receipts }.map { it.sha256 }.toSet()
            val foundDuplicates = claims.duplicateReceiptDigests(company, claim.id, digests)
            if (foundDuplicates is Result.Failed) return@run foundDuplicates
            val duplicates =
                duplicateExpenseReceiptDigests(
                    submission.lines,
                    (foundDuplicates as Result.Success).value,
                )
            val planned =
                planExpenseReview(
                    claim,
                    submission,
                    approval,
                    live,
                    beneficiary,
                    decision,
                    reason,
                    delegations,
                    grants,
                    duplicates,
                    acknowledgeDuplicates,
                    now,
                )
            if (planned is Result.Failed) return@run planned
            val plan = (planned as Result.Success).value
            approvals
                .decide(
                    actor,
                    approval.id,
                    approval.version,
                    approval.currentStep,
                    plan.transition,
                    reason,
                    now,
                )
                .flatMap { claims.review(actor, claim, plan.review) }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "expense_claim",
                                    claim.id,
                                    "expenses.submission_reviewed",
                                    mapOf(
                                        "submissionId" to id.toString(),
                                        "approvalId" to approval.id.toString(),
                                        "decision" to decision.name,
                                        "status" to plan.review.status.name,
                                        "duplicateAcknowledged" to acknowledgeDuplicates.toString(),
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
