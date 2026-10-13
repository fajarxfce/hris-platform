package dev.fajar.hris.expenses.delivery.responses

import java.util.UUID

data class ExpenseDraftReferencesResponse(
    val employmentId: UUID,
    val categories: List<ExpenseDraftLabelReferenceResponse>,
    val costCenters: List<ExpenseDraftLabelReferenceResponse>,
    val receipts: List<ExpenseDraftReceiptReferenceResponse>,
)
