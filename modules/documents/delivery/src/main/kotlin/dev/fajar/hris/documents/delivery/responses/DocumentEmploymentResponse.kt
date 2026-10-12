package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentEmployment
import java.util.UUID

data class DocumentEmploymentResponse(
    val id: UUID,
    val employeeNumber: String,
    val status: String,
    val version: Long,
)

fun DocumentEmployment.toResponse() =
    DocumentEmploymentResponse(id, employeeNumber, status.name, version)
