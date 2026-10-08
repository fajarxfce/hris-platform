package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpensePaymentItemResponse(
    val id: UUID,
    val claimId: UUID,
    val submissionId: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val amount: String,
    val currency: String,
    val destination: ExpensePaymentDestinationResponse,
    val status: String,
    val version: Int,
    val batchVersion: Long,
    val transactionReference: String?,
    val occurredAt: Instant?,
)
