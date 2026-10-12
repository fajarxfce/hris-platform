package dev.fajar.hris.documents.domain.entities

import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.util.UUID

data class DocumentEmployee(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val status: EmploymentStatus,
    val version: Long,
)
