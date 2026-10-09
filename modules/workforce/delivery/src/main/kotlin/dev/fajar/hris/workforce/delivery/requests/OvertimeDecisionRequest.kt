package dev.fajar.hris.workforce.delivery.requests

import dev.fajar.hris.approvals.domain.entities.ApprovalDecision

data class OvertimeDecisionRequest(
    val expectedVersion: Long,
    val decision: ApprovalDecision,
    val reason: String,
)
