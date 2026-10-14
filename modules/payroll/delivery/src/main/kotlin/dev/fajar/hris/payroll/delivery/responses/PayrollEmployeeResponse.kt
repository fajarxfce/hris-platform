package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollEmployeeResponse(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val employmentVersion: Long,
)
