package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollReviewDetailsResponse(
    val review: PayrollReviewResponse,
    val approval: PayrollReviewApprovalResponse,
    val changes: List<PayrollReviewChangeResponse>,
)
