package dev.fajar.hris.expenses.domain.entities

import java.time.Instant
import java.util.UUID

data class ExpensePaymentProgress(
    val batchId: UUID,
    val itemId: UUID,
    val status: ExpensePaymentItemStatus,
    val createdAt: Instant,
    val releasedAt: Instant?,
    val settledAt: Instant?,
)
