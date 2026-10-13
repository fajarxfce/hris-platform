package dev.fajar.hris.expenses.delivery.responses

import dev.fajar.hris.core.domain.Page

data class ExpensePaymentReviewResponse(
    val batch: ExpensePaymentBatchResponse,
    val history: Page<ExpensePaymentActionResponse>,
    val results: List<ExpensePaymentReconciliationResponse>,
    val availableActions: Set<String>,
)
