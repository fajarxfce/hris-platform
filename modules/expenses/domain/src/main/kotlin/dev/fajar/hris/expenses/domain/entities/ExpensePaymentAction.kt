package dev.fajar.hris.expenses.domain.entities

import java.time.Instant
import java.util.UUID

data class ExpensePaymentAction(
    val batchId: UUID,
    val version: Long,
    val kind: ExpensePaymentActionKind,
    val status: ExpensePaymentBatchStatus,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
