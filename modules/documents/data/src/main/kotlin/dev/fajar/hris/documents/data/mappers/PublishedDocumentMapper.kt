package dev.fajar.hris.documents.data.mappers

import dev.fajar.hris.documents.data.models.PublishedDocumentData
import dev.fajar.hris.documents.domain.entities.*
import java.util.UUID

fun PublishedDocumentData.toCatalogRecord(companyId: UUID, id: UUID): DocumentCatalogRecord {
    val header = document.toDocument()
    require(header.id == id)
    return DocumentCatalogRecord(
        header.id,
        header.version,
        header.employmentId,
        header.classification,
        toEvidence(companyId, header.employmentId, header.classification),
        header.metadata,
        header.metadataVersion,
    )
}

fun PublishedDocumentData.toEvidence(
    companyId: UUID,
    employmentId: UUID,
    classification: DocumentClassification,
): DocumentEvidence {
    require(document.companyId == companyId && revision.companyId == companyId)
    val header = document.toDocument()
    val accepted = revision.toDocumentRevision()
    require(header.employmentId == employmentId && header.classification == classification)
    require(header.id == accepted.documentId && header.currentRevisionId == accepted.id)
    require(accepted.status == DocumentRevisionStatus.READY)
    return DocumentEvidence(
        header.id,
        accepted.id,
        accepted.number,
        header.title,
        accepted.fileName,
        accepted.mediaType,
        accepted.size,
        accepted.sha256,
        requireNotNull(accepted.validatedAt),
    )
}
