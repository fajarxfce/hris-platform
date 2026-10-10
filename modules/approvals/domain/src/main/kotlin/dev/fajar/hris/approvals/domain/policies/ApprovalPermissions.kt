package dev.fajar.hris.approvals.domain.policies

import dev.fajar.hris.approvals.domain.entities.ApprovalKind
import dev.fajar.hris.core.domain.Actor

fun canSelectApprovalAssignees(actor: Actor, kind: ApprovalKind): Boolean =
    "approvals.manage" in actor.permissions ||
        ("approvals.read" in actor.permissions &&
            approvalPermissions(kind).any { it in actor.permissions })

fun approvalPermissions(kind: ApprovalKind): Set<String> =
    when (kind) {
        ApprovalKind.LEAVE,
        ApprovalKind.LEAVE_CANCELLATION -> setOf("leave.approve", "leave.team.approve")
        ApprovalKind.EXPENSE -> setOf("expenses.approve", "expenses.team.approve")
        ApprovalKind.OVERTIME -> setOf("overtime.approve", "overtime.team.approve")
        ApprovalKind.PAYROLL -> setOf("payroll.review")
    }
