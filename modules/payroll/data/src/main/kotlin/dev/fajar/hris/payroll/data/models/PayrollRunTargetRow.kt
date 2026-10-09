package dev.fajar.hris.payroll.data.models

import java.time.*
import java.util.UUID

data class PayrollRunTargetRow(
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val employmentVersion: Long,
    val compensationRevision: Long?,
    val inputId: UUID?,
    val inputRevision: Long?,
    val taxOpeningId: UUID?,
    val taxOpeningRevision: Long?,
)
