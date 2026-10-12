package dev.fajar.hris.documents.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate
import java.util.UUID

data class DocumentEmployeeDirectory(
    val companyId: UUID,
    val asOf: LocalDate,
    val timezone: String,
    val query: String,
    val employees: Page<DocumentEmployee>,
)
