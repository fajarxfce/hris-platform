package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.*
import java.time.*
import java.util.UUID

data class PayrollRunTarget(
    val ordinal: Int,
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
