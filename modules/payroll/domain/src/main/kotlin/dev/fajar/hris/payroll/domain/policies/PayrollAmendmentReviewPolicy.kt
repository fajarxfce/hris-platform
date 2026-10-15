package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.decideApproval
import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

const val PAYROLL_AMENDMENT_MAXIMUM_REVIEWS = 8
// Submission, eight stages, withdrawal and two publication transitions must fit.
const val PAYROLL_AMENDMENT_REVIEW_REVISION_RESERVE = 12L

fun requirePayrollAmendmentRead(actor: Actor): Result<Unit> =
    if (
        "payroll.amend" in actor.permissions ||
            "payroll.amend.review" in actor.permissions ||
            "payroll.amend.publish" in actor.permissions
    )
        Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))

fun validatePayrollAmendmentSubmission(
    amendment: PayrollAmendmentSummary,
    calculation: PayrollAmendmentCalculationSummary,
    latest: PayrollAmendmentReview?,
): Result<Unit> =
    when {
        amendment.status != PayrollAmendmentStatus.CALCULATED ||
            calculation.status != PayrollAmendmentCalculationStatus.SUCCEEDED ||
            calculation.id != amendment.latestCalculationId ||
            calculation.amendmentId != amendment.id ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_calculation_required"))
        latest?.status in
            setOf(PayrollAmendmentReviewStatus.PENDING, PayrollAmendmentReviewStatus.APPROVED) ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_review_active"))
        latest?.status == PayrollAmendmentReviewStatus.REJECTED &&
            latest.calculationId == calculation.id ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_recalculation_required"))
        (latest?.number ?: 0) >= PAYROLL_AMENDMENT_MAXIMUM_REVIEWS ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_review_limit"))
        amendment.version >
            PAYROLL_AMENDMENT_MAXIMUM_VERSION - PAYROLL_AMENDMENT_REVIEW_REVISION_RESERVE ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_revision_limit"))
        else -> Result.Success(Unit)
    }

/** Review thresholds measure each month's largest change; opposing months cannot cancel out. */
fun payrollAmendmentReviewExposure(preview: PayrollAmendmentPreview): BigDecimal {
    require(preview.calculations.size in 1..12)
    return preview.calculations.fold(BigDecimal.ZERO) { total, month ->
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val delta = month.difference
        total +
            listOf(
                    delta.taxableGross,
                    delta.withheld,
                    delta.takeHome,
                    delta.employeeDeductions,
                    delta.employerCost,
                )
                .maxOf { it.abs() }
    }
}

fun payrollAmendmentReviewStatus(status: ApprovalStatus): PayrollAmendmentReviewStatus =
    when (status) {
        ApprovalStatus.PENDING,
        ApprovalStatus.BLOCKED -> PayrollAmendmentReviewStatus.PENDING
        ApprovalStatus.APPROVED -> PayrollAmendmentReviewStatus.APPROVED
        ApprovalStatus.REJECTED -> PayrollAmendmentReviewStatus.REJECTED
        ApprovalStatus.CANCELLED -> PayrollAmendmentReviewStatus.WITHDRAWN
    }

/** Add live ownership to the retained exclusions without changing the stored approval snapshot. */
fun payrollAmendmentDecisionScope(
    approval: ApprovalRequest,
    beneficiaryId: UUID?,
): ApprovalRequest =
    if (beneficiaryId == null) approval
    else approval.copy(excludedAccountIds = approval.excludedAccountIds + beneficiaryId)

fun requirePayrollAmendmentDecisionActor(
    actor: Actor,
    approval: ApprovalRequest,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    at: Instant,
    security: IdentitySecurityPolicy,
): Result<Unit> {
    val assurance = requirePayrollMutation(actor, "payroll.amend.review", at, security)
    if (assurance is Result.Failed) return assurance
    if (
        actor.accountId == approval.authorId ||
            actor.accountId == approval.requesterId ||
            actor.accountId in approval.excludedAccountIds
    )
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "self_approval_denied"))
    return if (isAssignedApprover(actor, approval, delegations, members, at)) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.FORBIDDEN, "not_assigned_approver"))
}

fun availablePayrollAmendmentReviewActions(
    actor: Actor,
    amendment: PayrollAmendmentSummary,
    review: PayrollAmendmentReview,
    approval: ApprovalRequest,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    at: Instant,
): Set<PayrollAmendmentReviewAvailableAction> = buildSet {
    if (
        amendment.latestReviewId != review.id ||
            amendment.latestCalculationId != review.calculationId ||
            amendment.status !in
                setOf(PayrollAmendmentStatus.IN_REVIEW, PayrollAmendmentStatus.APPROVED)
    )
        return@buildSet
    if (
        "payroll.amend" in actor.permissions &&
            review.status in
                setOf(PayrollAmendmentReviewStatus.PENDING, PayrollAmendmentReviewStatus.APPROVED)
    )
        add(PayrollAmendmentReviewAvailableAction.WITHDRAW)
    if (
        review.status == PayrollAmendmentReviewStatus.PENDING &&
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
        add(PayrollAmendmentReviewAvailableAction.APPROVE)
        add(PayrollAmendmentReviewAvailableAction.REJECT)
    }
}
