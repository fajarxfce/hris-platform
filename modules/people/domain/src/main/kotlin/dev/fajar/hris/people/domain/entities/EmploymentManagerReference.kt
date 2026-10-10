package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class EmploymentManagerReference(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val working: Boolean,
)
