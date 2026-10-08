package dev.fajar.hris.people.data.models

import java.time.LocalDate
import java.util.UUID

data class EmployeeDraftData(
    val id: UUID,
    val employeeNumber: String,
    val personId: UUID,
    val legalName: String,
    val nationality: String,
    val email: String?,
    val birthDate: LocalDate?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val contract: String,
    val branchId: UUID?,
    val departmentId: UUID?,
    val positionId: UUID?,
    val costCenterId: UUID?,
    val managerId: UUID?,
)
