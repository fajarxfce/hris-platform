package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class EmploymentTransferCommand(
    val targetCompanyId: UUID,
    val targetEmploymentId: UUID,
    val expectedVersion: Long,
    val employeeNumber: String,
    val terms: EmploymentTerms,
    val reason: String,
)
