package dev.fajar.hris.expenses.domain.policies

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.identity.domain.entities.MemberAccount
import java.time.Instant
import java.util.UUID

fun excludedExpenseReviewers(submission: ExpenseSubmission, currentBeneficiary: UUID?): Set<UUID> =
    submission.makerIds + listOfNotNull(submission.requesterId, currentBeneficiary)

fun hasExpenseReviewScope(
    submission: ExpenseSubmission,
    approval: ApprovalRequest,
    actor: Actor,
    currentBeneficiary: UUID?,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    at: Instant,
): Boolean {
    if (delegations.size > 200) return false
    val excluded = excludedExpenseReviewers(submission, currentBeneficiary)
    return actor.accountId !in excluded &&
        isAssignedApprover(
            actor,
            approval,
            delegations.filter { it.fromAccount !in excluded },
            members,
            at,
        )
}

fun duplicateExpenseReceiptDigests(
    lines: List<ExpenseSubmittedLine>,
    otherClaims: Set<String>,
): Set<String> {
    val counts = lines.flatMap { it.receipts }.groupingBy { it.sha256 }.eachCount()
    return (otherClaims intersect counts.keys) + counts.filterValues { it > 1 }.keys
}

fun planExpenseReview(
    claim: ExpenseClaim,
    submission: ExpenseSubmission,
    approval: ApprovalRequest,
    actor: Actor,
    currentBeneficiary: UUID?,
    decision: ExpenseDecision,
    reason: String,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    duplicateDigests: Set<String>,
    acknowledged: Boolean,
    at: Instant,
): Result<ExpenseReviewPlan> {
    if (delegations.size > 200)
        return Result.Failed(Failure(FailureKind.CONFLICT, "approval_delegation_capacity"))
    if (claim.status != ExpenseClaimStatus.PENDING || claim.latestSubmissionId != submission.id)
        return Result.Failed(Failure(FailureKind.CONFLICT, "expense_submission_not_current"))
    val excluded = excludedExpenseReviewers(submission, currentBeneficiary)
    if (actor.accountId in excluded)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "self_approval_denied"))
    val transition =
        decideApproval(
            approval,
            actor,
            if (decision == ExpenseDecision.APPROVE) ApprovalDecision.APPROVE
            else ApprovalDecision.REJECT,
            reason,
            delegations.filter { it.fromAccount !in excluded },
            members,
            at,
        )
    if (transition is Result.Failed) return transition
    val next = (transition as Result.Success).value
    if (next.decidingFor in excluded)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "self_approval_denied"))
    if (
        decision == ExpenseDecision.APPROVE &&
            duplicateDigests.isNotEmpty() &&
            (!acknowledged || reason.isBlank())
    )
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "expense_duplicate_acknowledgement_required")
        )
    val status =
        when (decision) {
            ExpenseDecision.REJECT -> ExpenseClaimStatus.REJECTED
            ExpenseDecision.RETURN -> ExpenseClaimStatus.RETURNED
            ExpenseDecision.APPROVE ->
                if (next.status == ApprovalStatus.APPROVED) ExpenseClaimStatus.APPROVED
                else ExpenseClaimStatus.PENDING
        }
    return Result.Success(
        ExpenseReviewPlan(
            ExpenseReview(
                submission.id,
                claim.id,
                claim.version + 1,
                approval.id,
                approval.version + 1,
                approval.currentStep,
                actor.accountId,
                next.decidingFor,
                decision,
                status,
                duplicateDigests.toSet(),
                acknowledged,
                reason,
                at,
            ),
            next,
        )
    )
}
