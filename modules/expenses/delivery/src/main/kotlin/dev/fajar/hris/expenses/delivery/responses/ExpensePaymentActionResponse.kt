package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpensePaymentActionResponse(
    val version: Long,
    val kind: String,
    val status: String,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
