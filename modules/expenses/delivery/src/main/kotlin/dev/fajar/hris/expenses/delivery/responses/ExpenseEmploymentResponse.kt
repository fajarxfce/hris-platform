package dev.fajar.hris.expenses.delivery.responses

import java.util.UUID

data class ExpenseEmploymentResponse(
    val id: UUID,
    val employeeNumber: String,
    val status: String,
    val version: Long,
)
