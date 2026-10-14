package dev.fajar.hris.payroll.data.models

import java.util.UUID

data class PayrollEmployeeRow(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val employmentVersion: Long,
)
