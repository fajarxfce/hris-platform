package dev.fajar.hris.payroll.data.models

import java.util.UUID

data class CompensationEmployeeRow(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val employmentVersion: Long,
)
