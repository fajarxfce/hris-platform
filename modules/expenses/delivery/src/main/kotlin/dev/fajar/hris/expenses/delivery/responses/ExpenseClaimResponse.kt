package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpenseClaimResponse(
    val id: UUID,
    val employmentId: UUID,
    val createdBy: UUID,
    val createdAt: Instant,
    val version: Long,
    val draftRevision: Int,
    val status: String,
    val draft: ExpenseDraftResponse,
)
