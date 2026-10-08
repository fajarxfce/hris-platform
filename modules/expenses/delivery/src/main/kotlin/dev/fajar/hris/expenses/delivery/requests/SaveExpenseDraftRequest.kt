package dev.fajar.hris.expenses.delivery.requests

import java.util.UUID

data class SaveExpenseDraftRequest(
    val employmentId: UUID,
    val expectedVersion: Long? = null,
    val title: String,
    val description: String = "",
    val lines: List<ExpenseLineRequest> = emptyList(),
    val reason: String,
)
