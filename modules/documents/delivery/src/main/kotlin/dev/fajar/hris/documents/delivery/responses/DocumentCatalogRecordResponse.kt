package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentCatalogRecord
import dev.fajar.hris.documents.domain.entities.DocumentClassification
import java.util.UUID

data class DocumentCatalogRecordResponse(
    val id: UUID,
    val version: Long,
    val employmentId: UUID,
    val classification: DocumentClassification,
    val evidence: DocumentEvidenceResponse,
    val metadata: DocumentBusinessMetadataResponse,
    val metadataVersion: Long,
    val metadataSupported: Boolean = true,
)

fun DocumentCatalogRecord.toResponse() =
    DocumentCatalogRecordResponse(
        id,
        version,
        employmentId,
        classification,
        evidence.toResponse(),
        metadata.toResponse(),
        metadataVersion,
    )
