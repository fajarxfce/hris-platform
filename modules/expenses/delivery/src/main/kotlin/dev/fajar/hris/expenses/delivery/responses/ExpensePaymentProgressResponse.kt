package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpensePaymentProgressResponse(
    val batchId: UUID,
    val itemId: UUID,
    val status: String,
    val createdAt: Instant,
    val releasedAt: Instant?,
    val settledAt: Instant?,
)
