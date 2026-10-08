package dev.fajar.hris.expenses.domain.entities

import java.util.UUID

data class ExpenseReceipt(
    val submissionId: UUID,
    val claimId: UUID,
    val employmentId: UUID,
    val approvalId: UUID,
    val revisionId: UUID,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
)
