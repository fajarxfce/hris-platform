package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.approvals.domain.entities.ApprovalStatus
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*

fun leaveDecisionOutcome(
    status: LeaveStatus,
    approval: ApprovalStatus,
): Result<LeaveDecisionOutcome> {
    val cancellation = status == LeaveStatus.CANCELLATION_PENDING
    if (status != LeaveStatus.PENDING && !cancellation)
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_not_pending"))
    return when (approval) {
        ApprovalStatus.PENDING,
        ApprovalStatus.BLOCKED -> Result.Success(LeaveDecisionOutcome(status, null))
        ApprovalStatus.APPROVED ->
            Result.Success(
                LeaveDecisionOutcome(
                    if (cancellation) LeaveStatus.CANCELLED else LeaveStatus.APPROVED,
                    if (cancellation) LeaveBalanceEffect.REFUND else LeaveBalanceEffect.CONSUME,
                )
            )
        ApprovalStatus.REJECTED ->
            Result.Success(
                LeaveDecisionOutcome(
                    if (cancellation) LeaveStatus.APPROVED else LeaveStatus.REJECTED,
                    if (cancellation) null else LeaveBalanceEffect.RELEASE,
                )
            )
        ApprovalStatus.CANCELLED -> Result.Failed(Failure(FailureKind.CONFLICT, "approval_changed"))
    }
}

fun canManageLeave(actor: Actor, request: LeaveRequest): Boolean =
    "leave.manage" in actor.permissions ||
        ("leave.self.manage" in actor.permissions && request.ownerAccountId == actor.accountId)
