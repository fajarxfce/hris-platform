package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpensePaymentResultResponse(
    val itemId: UUID,
    val status: String,
    val transactionReference: String?,
    val occurredAt: Instant,
    val reason: String,
)
