package dev.fajar.hris.payroll.data.models

import java.time.*
import java.util.UUID

data class PayrollEmploymentTermsData(
    val effectiveFrom: LocalDate,
    val contract: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: String,
    val branchId: UUID?,
    val departmentId: UUID?,
    val positionId: UUID?,
    val costCenterId: UUID?,
    val managerId: UUID?,
)
