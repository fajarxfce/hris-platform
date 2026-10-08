package dev.fajar.hris.expenses.domain.entities

import java.time.Instant
import java.util.UUID

data class ExpenseClaimChange(
    val claimId: UUID,
    val version: Long,
    val draftRevision: Int,
    val status: ExpenseClaimStatus,
    val kind: ExpenseClaimChangeKind,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
