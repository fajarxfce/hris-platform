package dev.fajar.hris.documents.domain.entities

import java.util.UUID

/**
 * Published metadata, versioned by its document header. Content requires separate authorization.
 */
data class DocumentCatalogRecord(
    val id: UUID,
    val version: Long,
    val employmentId: UUID,
    val classification: DocumentClassification,
    val evidence: DocumentEvidence,
)
