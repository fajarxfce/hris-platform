package dev.fajar.hris.expenses.domain.entities

import java.util.UUID

data class SelfExpenseDraftReferences(
    val employmentId: UUID,
    val categories: List<ExpenseDraftLabelReference>,
    val costCenters: List<ExpenseDraftLabelReference>,
    val receipts: List<ExpenseDraftReceiptReference>,
)
