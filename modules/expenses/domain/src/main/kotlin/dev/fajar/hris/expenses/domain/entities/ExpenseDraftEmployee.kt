package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.util.UUID

data class ExpenseDraftEmployee(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val status: EmploymentStatus,
    val version: Long,
)
