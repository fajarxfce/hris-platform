package dev.fajar.hris.documents.data.mappers

import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.schema.tables.records.DocumentMetadataRevisionsRecord
import java.time.ZoneOffset
import java.util.UUID

fun DocumentMetadataRevisionsRecord.toMetadataRevision() =
    DocumentMetadataRevision(
        documentId,
        version,
        documentVersion,
        DocumentBusinessMetadata(
            DocumentCategory.valueOf(category),
            identityNumber,
            issuingCountry,
            issuedOn,
            validUntil,
        ),
        actorId,
        recordedAt.toInstant(),
        reason,
    )

fun DocumentMetadataRevision.toMetadataRow(companyId: UUID) =
    DocumentMetadataRevisionsRecord().also { row ->
        row.companyId = companyId
        row.documentId = documentId
        row.version = version
        row.documentVersion = documentVersion
        row.category = metadata.category.name
        row.identityNumber = metadata.identityNumber
        row.issuingCountry = metadata.issuingCountry
        row.issuedOn = metadata.issuedOn
        row.validUntil = metadata.validUntil
        row.actorId = actorId
        row.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        row.reason = reason
    }
