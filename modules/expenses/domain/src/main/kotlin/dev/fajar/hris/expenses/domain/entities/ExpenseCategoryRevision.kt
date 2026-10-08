package dev.fajar.hris.expenses.domain.entities

import java.time.Instant
import java.util.UUID

data class ExpenseCategoryRevision(
    val category: ExpenseCategory,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
