package dev.fajar.hris.leave.delivery.requests

import dev.fajar.hris.approvals.domain.entities.ApprovalDecision

data class LeaveDecisionRequest(
    val version: Long,
    val decision: ApprovalDecision,
    val reason: String = "",
)
