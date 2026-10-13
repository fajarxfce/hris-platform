package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.util.UUID

data class LeavePreparationEmployee(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val status: EmploymentStatus,
    val version: Long,
)
