package dev.fajar.hris.expenses.data.models

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class ExpenseClaimSummaryRow(
    val id: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val totalAmount: BigDecimal,
    val createdAt: OffsetDateTime,
    val status: String,
    val version: Long,
)
