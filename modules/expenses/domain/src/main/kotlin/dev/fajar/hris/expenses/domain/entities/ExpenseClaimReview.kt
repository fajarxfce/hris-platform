package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.core.domain.Page

data class ExpenseClaimReview(
    val details: ExpenseClaimDetails,
    val history: Page<ExpenseClaimChange>,
    val submissions: Page<ExpenseSubmissionSummary>,
    val payments: List<ExpensePaymentProgress>,
)
