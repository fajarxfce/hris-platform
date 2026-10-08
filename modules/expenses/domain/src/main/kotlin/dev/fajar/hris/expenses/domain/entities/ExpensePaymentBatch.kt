package dev.fajar.hris.expenses.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ExpensePaymentBatch(
    val id: UUID,
    val status: ExpensePaymentBatchStatus,
    val version: Long,
    val title: String,
    val totalAmount: BigDecimal,
    val itemCount: Int,
    val createdBy: UUID,
    val createdAt: Instant,
    val releasedBy: UUID?,
    val releasedAt: Instant?,
    val items: List<ExpensePaymentItem>,
)
