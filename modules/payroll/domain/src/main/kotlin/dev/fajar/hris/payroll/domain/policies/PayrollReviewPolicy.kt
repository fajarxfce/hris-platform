package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.decideApproval
import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.payroll.domain.entities.*
import java.time.Instant

fun validatePayrollReviewSubmission(
    run: PayrollRun,
    latest: PayrollReview?,
    totals: PayrollRunTotals,
): Result<Unit> =
    when {
        run.status != PayrollRunStatus.CALCULATED ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_calculation_required"))
        run.processed != run.totalEmployees ||
            run.failed != 0 ||
            totals.employeeCount != run.totalEmployees ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_calculation_has_failures"))
        latest?.status in setOf(PayrollReviewStatus.PENDING, PayrollReviewStatus.APPROVED) ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_review_active"))
        latest?.status == PayrollReviewStatus.REJECTED ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_recalculation_required"))
        (latest?.number ?: 0) >= 8 ->
            Result.Failed(
                Failure(
                    FailureKind.CONFLICT,
                    "payroll_review_capacity",
                    parameters = mapOf("maximum" to "8"),
                )
            )
        else -> Result.Success(Unit)
    }

fun payrollReviewStatus(status: ApprovalStatus): PayrollReviewStatus =
    when (status) {
        ApprovalStatus.PENDING,
        ApprovalStatus.BLOCKED -> PayrollReviewStatus.PENDING
        ApprovalStatus.APPROVED -> PayrollReviewStatus.APPROVED
        ApprovalStatus.REJECTED -> PayrollReviewStatus.REJECTED
        ApprovalStatus.CANCELLED -> PayrollReviewStatus.WITHDRAWN
    }

fun availablePayrollReviewActions(
    actor: Actor,
    review: PayrollReview,
    run: PayrollRun,
    approval: ApprovalRequest,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    finalizationActive: Boolean,
    at: Instant,
): Set<PayrollReviewAvailableAction> = buildSet {
    if (run.status != PayrollRunStatus.CALCULATED || run.version != review.runVersion)
        return@buildSet
    if (
        "payroll.calculate" in actor.permissions &&
            review.status in setOf(PayrollReviewStatus.PENDING, PayrollReviewStatus.APPROVED) &&
            !finalizationActive
    )
        add(PayrollReviewAvailableAction.WITHDRAW)
    if (
        review.status == PayrollReviewStatus.PENDING &&
            decideApproval(
                approval,
                actor,
                ApprovalDecision.APPROVE,
                "",
                delegations,
                members,
                at,
            ) is
                Result.Success
    ) {
        add(PayrollReviewAvailableAction.APPROVE)
        add(PayrollReviewAvailableAction.REJECT)
    }
}

/** Original receipts retain assignment/delegation authority even after the stage has advanced. */
fun requirePayrollReviewDecisionActor(
    actor: Actor,
    approval: ApprovalRequest,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    at: Instant,
    security: IdentitySecurityPolicy,
): Result<Unit> {
    val assurance = requirePayrollMutation(actor, "payroll.review", at, security)
    if (assurance is Result.Failed) return assurance
    if (actor.accountId == approval.authorId || actor.accountId in approval.excludedAccountIds)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "self_approval_denied"))
    return if (isAssignedApprover(actor, approval, delegations, members, at)) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.FORBIDDEN, "not_assigned_approver"))
}
