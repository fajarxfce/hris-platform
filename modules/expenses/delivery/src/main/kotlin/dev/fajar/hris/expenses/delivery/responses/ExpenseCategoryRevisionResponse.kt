package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpenseCategoryRevisionResponse(
    val category: ExpenseCategoryResponse,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
