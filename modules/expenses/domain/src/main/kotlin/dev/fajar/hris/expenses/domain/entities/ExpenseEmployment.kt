package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.util.UUID

/** Self-service selection without personal-profile or administrative assignment fields. */
data class ExpenseEmployment(
    val id: UUID,
    val employeeNumber: String,
    val status: EmploymentStatus,
    val version: Long,
)
