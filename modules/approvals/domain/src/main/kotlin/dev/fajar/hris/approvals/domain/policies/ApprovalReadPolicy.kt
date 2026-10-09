package dev.fajar.hris.approvals.domain.policies

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.identity.domain.entities.MemberAccount
import java.time.Instant

fun isAssignedApprover(
    actor: Actor,
    request: ApprovalRequest,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    at: Instant,
): Boolean {
    if (actor.permissions.none { it in approvalPermissions(request.kind) }) return false
    if (
        actor.accountId == request.authorId ||
            actor.accountId == request.requesterId ||
            actor.accountId in request.excludedAccountIds
    )
        return false
    val assigned = request.stages.flatMap { it.assignees }.toSet()
    if (actor.accountId in assigned) return true
    if (delegations.size > 200) return false
    return delegations.any { delegation ->
        delegation.active &&
            delegation.toAccount == actor.accountId &&
            delegation.kind == request.kind &&
            delegation.fromAccount in assigned &&
            delegation.fromAccount !in request.excludedAccountIds &&
            delegation.fromAccount != request.authorId &&
            delegation.fromAccount != request.requesterId &&
            !at.isBefore(delegation.validFrom) &&
            at.isBefore(delegation.validUntil) &&
            members.any {
                it.id == delegation.fromAccount &&
                    it.accountActive &&
                    it.membershipActive &&
                    it.permissions.any { permission ->
                        permission in approvalPermissions(request.kind)
                    }
            }
    }
}
