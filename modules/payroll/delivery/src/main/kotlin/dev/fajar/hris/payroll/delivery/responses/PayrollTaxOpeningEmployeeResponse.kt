package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollTaxOpeningEmployeeResponse(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val employmentVersion: Long,
    val openingVersion: Long?,
    val openingStatus: String?,
)
