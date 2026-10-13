package dev.fajar.hris.expenses.delivery.responses

import dev.fajar.hris.core.domain.Page

data class ExpensePolicyReviewResponse(
    val current: ExpenseCategoryResponse,
    val history: Page<ExpenseCategoryRevisionResponse>,
)
