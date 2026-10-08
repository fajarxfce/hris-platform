package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpenseClaimChangeResponse(
    val version: Long,
    val draftRevision: Int,
    val status: String,
    val kind: String,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
