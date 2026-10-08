package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.decideApproval
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.leave.domain.entities.*
import java.time.Instant

fun leaveAvailableActions(
    actor: Actor,
    request: LeaveRequest,
    approval: ApprovalRequest,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    at: Instant,
    beneficiaryAccountId: java.util.UUID? = request.ownerAccountId,
): Set<LeaveAction> = buildSet {
    if (canManageLeave(actor, request)) {
        when (request.status) {
            LeaveStatus.PENDING,
            LeaveStatus.CANCELLATION_PENDING -> add(LeaveAction.WITHDRAW)
            LeaveStatus.APPROVED -> add(LeaveAction.REQUEST_CANCELLATION)
            else -> Unit
        }
    }
    if (request.status in setOf(LeaveStatus.PENDING, LeaveStatus.CANCELLATION_PENDING)) {
        val decision =
            decideApproval(approval, actor, ApprovalDecision.APPROVE, "", delegations, members, at)
        if (
            decision is Result.Success &&
                beneficiaryAccountId !in setOf(actor.accountId, decision.value.decidingFor)
        )
            add(LeaveAction.DECIDE)
    }
}
