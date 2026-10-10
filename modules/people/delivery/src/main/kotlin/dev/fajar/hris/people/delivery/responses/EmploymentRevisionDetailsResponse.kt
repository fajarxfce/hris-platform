package dev.fajar.hris.people.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class EmploymentRevisionDetailsResponse(
    val employeeId: UUID,
    val version: Long,
    val companyDate: LocalDate,
    val revision: RevisionResponse,
    val canCancel: Boolean,
)
