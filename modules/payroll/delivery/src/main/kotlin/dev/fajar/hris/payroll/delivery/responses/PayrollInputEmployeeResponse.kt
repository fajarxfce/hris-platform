package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollInputEmployeeResponse(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val employmentVersion: Long,
    val inputVersion: Long?,
    val inputStatus: String?,
)
