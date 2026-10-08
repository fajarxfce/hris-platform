package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class EmployeeDraft(
    val id: UUID,
    val employeeNumber: String,
    val person: PersonProfile,
    val terms: EmploymentTerms,
)
