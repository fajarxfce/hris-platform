package dev.fajar.hris.expenses.domain.entities

import java.time.Instant
import java.util.UUID

data class ExpensePaymentResult(
    val itemId: UUID,
    val status: ExpensePaymentItemStatus,
    val transactionReference: String?,
    val occurredAt: Instant,
    val reason: String,
    val confirmedNoTransfer: Boolean = false,
)
