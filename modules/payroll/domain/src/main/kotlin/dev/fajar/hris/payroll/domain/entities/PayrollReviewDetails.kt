package dev.fajar.hris.payroll.domain.entities

import java.time.*

data class PayrollReviewDetails(
    val review: PayrollReview,
    val approval: dev.fajar.hris.approvals.domain.entities.ApprovalRequest,
    val changes: List<PayrollReviewChange>,
)
