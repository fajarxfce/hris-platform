package dev.fajar.hris.expenses.data.models

import dev.fajar.hris.schema.tables.records.ExpenseCategoryRevisionsRecord

data class ExpenseCategoryRow(
    val code: String,
    val version: Long,
    val revision: ExpenseCategoryRevisionsRecord,
)
