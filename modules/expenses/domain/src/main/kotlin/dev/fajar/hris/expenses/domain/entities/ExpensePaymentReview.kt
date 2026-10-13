package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.core.domain.Page

data class ExpensePaymentReview(
    val batch: ExpensePaymentBatch,
    val history: Page<ExpensePaymentAction>,
    val results: List<ExpensePaymentReconciliation>,
    val availableActions: Set<ExpensePaymentAvailableAction>,
)
