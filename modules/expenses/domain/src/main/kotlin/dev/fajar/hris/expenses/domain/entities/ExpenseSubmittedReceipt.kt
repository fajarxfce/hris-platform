package dev.fajar.hris.expenses.domain.entities

import java.util.UUID

data class ExpenseSubmittedReceipt(
    val revisionId: UUID,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
    val possibleDuplicate: Boolean,
)
