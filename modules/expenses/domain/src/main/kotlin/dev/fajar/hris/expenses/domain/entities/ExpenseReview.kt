package dev.fajar.hris.expenses.domain.entities

import java.time.Instant
import java.util.UUID

data class ExpenseReview(
    val submissionId: UUID,
    val claimId: UUID,
    val claimVersion: Long,
    val approvalId: UUID,
    val approvalVersion: Long,
    val step: Int,
    val actorId: UUID,
    val decidingFor: UUID,
    val decision: ExpenseDecision,
    val status: ExpenseClaimStatus,
    val duplicateDigests: Set<String>,
    val duplicatesAcknowledged: Boolean,
    val reason: String,
    val decidedAt: Instant,
)
