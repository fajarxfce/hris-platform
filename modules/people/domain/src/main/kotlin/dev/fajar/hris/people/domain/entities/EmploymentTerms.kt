package dev.fajar.hris.people.domain.entities

import java.time.LocalDate
import java.util.UUID

data class EmploymentTerms(
    val effectiveFrom: LocalDate,
    val contract: ContractKind,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: EmploymentStatus,
    val branchId: UUID?,
    val departmentId: UUID?,
    val positionId: UUID?,
    val costCenterId: UUID?,
    val managerId: UUID?,
) {
    fun isWorkingOn(date: LocalDate): Boolean =
        status in setOf(EmploymentStatus.ACTIVE, EmploymentStatus.PROBATION) &&
            !date.isBefore(startDate) &&
            (endDate == null || !date.isAfter(endDate))
}
