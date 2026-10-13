package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.core.domain.Page

data class ExpensePolicyReview(
    val current: ExpenseCategory,
    val history: Page<ExpenseCategoryRevision>,
)
