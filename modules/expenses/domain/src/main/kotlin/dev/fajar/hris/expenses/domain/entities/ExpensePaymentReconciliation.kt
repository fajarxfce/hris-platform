package dev.fajar.hris.expenses.domain.entities

import java.time.Instant
import java.util.UUID

data class ExpensePaymentReconciliation(
    val batchId: UUID,
    val batchVersion: Long,
    val actorId: UUID,
    val recordedAt: Instant,
    val resultingStatus: ExpensePaymentBatchStatus,
    val results: List<ExpensePaymentResult>,
)
