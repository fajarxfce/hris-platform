package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.documents.domain.entities.SelfDocumentEvidencePage
import java.util.UUID

data class SelfDocumentEvidenceResponse(
    val accountId: UUID,
    val companyId: UUID,
    val employmentId: UUID,
    val classification: String,
    val documents: Page<DocumentEvidenceResponse>,
)

fun SelfDocumentEvidencePage.toResponse() =
    SelfDocumentEvidenceResponse(
        accountId,
        companyId,
        employmentId,
        classification.name,
        Page(documents.items.map { it.toResponse() }, documents.nextCursor),
    )
