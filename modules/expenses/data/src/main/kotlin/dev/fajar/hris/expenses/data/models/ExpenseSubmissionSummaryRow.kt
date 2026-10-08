package dev.fajar.hris.expenses.data.models

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class ExpenseSubmissionSummaryRow(
    val id: UUID,
    val number: Int,
    val draftRevision: Int,
    val approvalId: UUID,
    val title: String,
    val totalAmount: BigDecimal,
    val submittedBy: UUID,
    val submittedAt: OffsetDateTime,
)
