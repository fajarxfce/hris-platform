package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpensePaymentReconciliationResponse(
    val batchVersion: Long,
    val actorId: UUID,
    val recordedAt: Instant,
    val resultingStatus: String,
    val results: List<ExpensePaymentResultResponse>,
)
