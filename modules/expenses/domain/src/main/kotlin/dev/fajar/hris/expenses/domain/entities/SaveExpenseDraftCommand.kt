package dev.fajar.hris.expenses.domain.entities

import java.util.UUID

data class SaveExpenseDraftCommand(
    val id: UUID,
    val employmentId: UUID,
    val expectedVersion: Long?,
    val title: String,
    val description: String,
    val lines: List<ExpenseLine>,
    val reason: String,
)
