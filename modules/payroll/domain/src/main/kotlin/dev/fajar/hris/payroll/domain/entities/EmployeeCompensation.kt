package dev.fajar.hris.payroll.domain.entities

import java.time.YearMonth
import java.util.UUID

data class EmployeeCompensation(
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val version: Long,
    val appliedRevision: Long,
    val effectiveFrom: YearMonth,
    val terms: CompensationTerms,
)
