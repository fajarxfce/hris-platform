package dev.fajar.hris.people.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class EmployeeImportProposalResponse(
    val employeeId: UUID,
    val employeeNumber: String,
    val legalName: String,
    val birthDate: LocalDate?,
    val nationality: String,
    val email: String?,
    val terms: TermsResponse,
)
