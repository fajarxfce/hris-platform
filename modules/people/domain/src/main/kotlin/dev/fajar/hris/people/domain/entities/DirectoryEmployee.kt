package dev.fajar.hris.people.domain.entities

import java.util.UUID

/** Company directory projection; it carries no private profile or employment terms. */
data class DirectoryEmployee(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val position: String?,
    val department: String?,
)
