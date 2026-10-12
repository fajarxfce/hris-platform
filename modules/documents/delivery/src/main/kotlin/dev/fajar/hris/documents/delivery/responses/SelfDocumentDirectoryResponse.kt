package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.documents.domain.entities.SelfDocumentDirectory
import java.time.LocalDate
import java.util.UUID

data class SelfDocumentDirectoryResponse(
    val accountId: UUID,
    val companyId: UUID,
    val asOf: LocalDate,
    val timezone: String,
    val employments: Page<DocumentEmploymentResponse>,
)

fun SelfDocumentDirectory.toResponse() =
    SelfDocumentDirectoryResponse(
        accountId,
        companyId,
        asOf,
        timezone,
        Page(employments.items.map { it.toResponse() }, employments.nextCursor),
    )
