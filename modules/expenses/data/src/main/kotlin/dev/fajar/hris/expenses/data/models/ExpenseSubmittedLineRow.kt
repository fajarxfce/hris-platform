package dev.fajar.hris.expenses.data.models

import dev.fajar.hris.schema.tables.records.*

data class ExpenseSubmittedLineRow(
    val line: ExpenseDraftLinesRecord,
    val snapshot: ExpenseSubmittedLinesRecord,
    val categoryCode: String,
    val category: ExpenseCategoryRevisionsRecord,
)
