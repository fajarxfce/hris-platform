package dev.fajar.hris.people.domain.entities

import java.time.LocalDate

data class EmploymentDetails(
    val asOf: LocalDate,
    val employee: Employee,
    val branch: EmploymentUnitReference?,
    val department: EmploymentUnitReference?,
    val position: EmploymentUnitReference?,
    val costCenter: EmploymentUnitReference?,
    val manager: EmploymentManagerReference?,
)
