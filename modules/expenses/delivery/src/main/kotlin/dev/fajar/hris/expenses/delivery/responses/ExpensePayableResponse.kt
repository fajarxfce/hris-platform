package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpensePayableResponse(
    val claimId: UUID,
    val submissionId: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val amount: String,
    val currency: String,
    val approvedAt: Instant,
)
