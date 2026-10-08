package dev.fajar.hris.people.domain.entities

import java.time.LocalDate
import java.util.UUID

data class ReportingAssignment(
    val employeeId: UUID,
    val managerId: UUID?,
    val effectiveFrom: LocalDate,
    val revision: Long,
)
