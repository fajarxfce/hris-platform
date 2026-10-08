package dev.fajar.hris.expenses.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ExpensePaymentItem(
    val id: UUID,
    val claimId: UUID,
    val submissionId: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val amount: BigDecimal,
    val destination: ExpensePaymentDestination,
    val status: ExpensePaymentItemStatus,
    val version: Int,
    val batchVersion: Long,
    val transactionReference: String?,
    val occurredAt: Instant?,
)
