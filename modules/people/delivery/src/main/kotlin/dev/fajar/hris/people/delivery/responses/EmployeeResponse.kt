package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class EmployeeResponse(
    val id: UUID,
    val companyId: UUID,
    val employeeNumber: String,
    val person: PersonSummaryResponse,
    val terms: TermsResponse,
    val version: Long,
    val appliedRevision: Long,
)
