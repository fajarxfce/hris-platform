package dev.fajar.hris.expenses.delivery.requests

import dev.fajar.hris.expenses.domain.entities.ExpensePaymentItemStatus
import java.time.Instant
import java.util.UUID

data class ExpensePaymentResultRequest(
    val itemId: UUID,
    val status: ExpensePaymentItemStatus,
    val transactionReference: String? = null,
    val occurredAt: Instant,
    val reason: String,
    val confirmedNoTransfer: Boolean = false,
)
