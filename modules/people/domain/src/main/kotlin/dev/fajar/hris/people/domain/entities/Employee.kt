package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class Employee(
    val id: UUID,
    val companyId: UUID,
    val employeeNumber: String,
    val person: PersonProfile,
    val terms: EmploymentTerms,
    val managerAccountId: UUID?,
    val version: Long,
    val appliedRevision: Long,
)
