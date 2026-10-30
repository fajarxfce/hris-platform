package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.approvals.domain.entities.ApprovalRequest
import dev.fajar.hris.approvals.domain.entities.Delegation
import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.leave.domain.entities.LeaveRequest
import dev.fajar.hris.people.domain.entities.Employee
import java.time.Instant

/** Ordinary request scope remains independent of a temporary approval delegation. */
fun hasLeaveRequestReadScope(
    actor: Actor,
    request: LeaveRequest,
    employee: Employee?,
    approval: ApprovalRequest?,
    delegations: List<Delegation>,
    members: List<MemberAccount>,
    at: Instant,
): Boolean =
    canReadLeaveRequest(actor, request, employee) ||
        (approval != null && isAssignedApprover(actor, approval, delegations, members, at))
