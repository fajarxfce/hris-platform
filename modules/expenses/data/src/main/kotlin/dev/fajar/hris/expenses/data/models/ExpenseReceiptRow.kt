package dev.fajar.hris.expenses.data.models

import dev.fajar.hris.schema.tables.records.ExpenseSubmittedReceiptsRecord
import java.util.UUID

data class ExpenseReceiptRow(
    val receipt: ExpenseSubmittedReceiptsRecord,
    val employmentId: UUID,
    val approvalId: UUID,
)
