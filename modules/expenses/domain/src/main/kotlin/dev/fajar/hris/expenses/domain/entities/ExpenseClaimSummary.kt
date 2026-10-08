package dev.fajar.hris.expenses.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ExpenseClaimSummary(
    val id: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val totalAmount: BigDecimal,
    val createdAt: Instant,
    val status: ExpenseClaimStatus,
    val version: Long,
)
