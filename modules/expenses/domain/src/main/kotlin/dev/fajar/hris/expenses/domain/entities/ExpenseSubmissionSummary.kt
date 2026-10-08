package dev.fajar.hris.expenses.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ExpenseSubmissionSummary(
    val id: UUID,
    val number: Int,
    val draftRevision: Int,
    val approvalId: UUID,
    val title: String,
    val totalAmount: BigDecimal,
    val submittedBy: UUID,
    val submittedAt: Instant,
)
