package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentEmployee
import java.util.UUID

data class DocumentEmployeeResponse(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val status: String,
    val version: Long,
)

fun DocumentEmployee.toResponse() =
    DocumentEmployeeResponse(id, employeeNumber, name, status.name, version)
