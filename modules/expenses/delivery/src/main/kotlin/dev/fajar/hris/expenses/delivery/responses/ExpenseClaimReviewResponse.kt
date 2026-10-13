package dev.fajar.hris.expenses.delivery.responses

import dev.fajar.hris.core.domain.Page

data class ExpenseClaimReviewResponse(
    val claim: ExpenseClaimResponse,
    val history: Page<ExpenseClaimChangeResponse>,
    val submissions: Page<ExpenseSubmissionSummaryResponse>,
    val payments: List<ExpensePaymentProgressResponse>,
)
