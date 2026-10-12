package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.documents.domain.entities.DocumentEmployeeDirectory
import java.time.LocalDate
import java.util.UUID

data class DocumentEmployeeDirectoryResponse(
    val companyId: UUID,
    val asOf: LocalDate,
    val timezone: String,
    val query: String,
    val employees: Page<DocumentEmployeeResponse>,
)

fun DocumentEmployeeDirectory.toResponse() =
    DocumentEmployeeDirectoryResponse(
        companyId,
        asOf,
        timezone,
        query,
        Page(employees.items.map { it.toResponse() }, employees.nextCursor),
    )
