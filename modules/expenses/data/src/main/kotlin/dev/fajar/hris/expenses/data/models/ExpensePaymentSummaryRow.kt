package dev.fajar.hris.expenses.data.models

import dev.fajar.hris.schema.tables.records.ExpensePaymentBatchesRecord

data class ExpensePaymentSummaryRow(
    val batch: ExpensePaymentBatchesRecord,
    val pending: Int,
    val succeeded: Int,
    val failed: Int,
)
