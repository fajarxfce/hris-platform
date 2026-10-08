package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpenseClaimSummaryResponse(
    val id: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val totalAmount: String,
    val currency: String,
    val createdAt: Instant,
    val status: String,
    val version: Long,
)
