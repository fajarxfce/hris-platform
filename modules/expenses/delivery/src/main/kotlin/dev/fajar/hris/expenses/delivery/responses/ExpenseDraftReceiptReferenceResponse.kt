package dev.fajar.hris.expenses.delivery.responses

import java.util.UUID

data class ExpenseDraftReceiptReferenceResponse(
    val revisionId: UUID,
    val documentId: UUID?,
    val fileName: String?,
    val mediaType: String?,
    val size: Long?,
    val status: String?,
)
