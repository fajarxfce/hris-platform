package dev.fajar.hris.people.domain.entities

import java.time.LocalDate
import java.util.UUID

data class EmploymentRevisionDetails(
    val employeeId: UUID,
    val version: Long,
    val companyDate: LocalDate,
    val revision: EmploymentRevision,
    val canCancel: Boolean,
)
