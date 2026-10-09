package dev.fajar.hris.payroll.delivery.requests

data class PayrollReviewDecisionRequest(
    val expectedVersion: Long,
    val expectedApprovalVersion: Long,
    val decision: dev.fajar.hris.approvals.domain.entities.ApprovalDecision,
    val reason: String = "",
)
