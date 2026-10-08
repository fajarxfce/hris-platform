package dev.fajar.hris.expenses.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpenseDraftResponse(
    val revision: Int,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val description: String,
    val totalAmount: String,
    val currency: String,
    val lines: List<ExpenseLineResponse>,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
