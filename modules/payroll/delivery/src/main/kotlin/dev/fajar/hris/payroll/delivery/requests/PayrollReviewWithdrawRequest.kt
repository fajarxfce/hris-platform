package dev.fajar.hris.payroll.delivery.requests

data class PayrollReviewWithdrawRequest(
    val expectedVersion: Long,
    val expectedApprovalVersion: Long,
    val reason: String,
)
