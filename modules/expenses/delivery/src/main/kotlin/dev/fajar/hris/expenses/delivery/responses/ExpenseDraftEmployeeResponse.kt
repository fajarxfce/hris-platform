package dev.fajar.hris.expenses.delivery.responses

import java.util.UUID

data class ExpenseDraftEmployeeResponse(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val status: String,
    val version: Long,
)
