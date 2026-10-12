package dev.fajar.hris.documents.domain.entities

import dev.fajar.hris.core.domain.Page
import java.util.UUID

data class SelfDocumentEvidencePage(
    val accountId: UUID,
    val companyId: UUID,
    val employmentId: UUID,
    val classification: DocumentClassification,
    val documents: Page<DocumentEvidence>,
)
