package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpenseSubmissionSummaryResponse(
    val id: UUID,
    val number: Int,
    val draftRevision: Int,
    val approvalId: UUID,
    val title: String,
    val totalAmount: String,
    val currency: String,
    val submittedBy: UUID,
    val submittedAt: Instant,
)
