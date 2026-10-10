package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class EmploymentUnitReference(
    val id: UUID,
    val code: String,
    val name: String,
    val active: Boolean,
)
