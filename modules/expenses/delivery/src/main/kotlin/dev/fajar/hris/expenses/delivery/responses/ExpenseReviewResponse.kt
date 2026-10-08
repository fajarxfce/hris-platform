package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpenseReviewResponse(
    val claimVersion: Long,
    val approvalVersion: Long,
    val step: Int,
    val actorId: UUID,
    val decidingFor: UUID,
    val decision: String,
    val status: String,
    val duplicateDigests: Set<String>,
    val duplicatesAcknowledged: Boolean,
    val reason: String,
    val decidedAt: Instant,
)
