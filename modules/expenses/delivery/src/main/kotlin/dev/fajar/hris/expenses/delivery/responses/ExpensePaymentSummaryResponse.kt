package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpensePaymentSummaryResponse(
    val id: UUID,
    val status: String,
    val version: Long,
    val title: String,
    val totalAmount: String,
    val currency: String,
    val itemCount: Int,
    val pendingCount: Int,
    val succeededCount: Int,
    val failedCount: Int,
    val createdBy: UUID,
    val createdAt: Instant,
)
