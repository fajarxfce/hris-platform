package dev.fajar.hris.expenses.delivery.responses

import java.util.UUID

data class ExpenseSubmittedReceiptResponse(
    val revisionId: UUID,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
    val possibleDuplicate: Boolean,
)
