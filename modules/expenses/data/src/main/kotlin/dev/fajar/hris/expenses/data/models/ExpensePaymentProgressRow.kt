package dev.fajar.hris.expenses.data.models

import java.time.OffsetDateTime
import java.util.UUID

data class ExpensePaymentProgressRow(
    val batchId: UUID,
    val itemId: UUID,
    val status: String,
    val createdAt: OffsetDateTime,
    val releasedAt: OffsetDateTime?,
    val settledAt: OffsetDateTime?,
)
