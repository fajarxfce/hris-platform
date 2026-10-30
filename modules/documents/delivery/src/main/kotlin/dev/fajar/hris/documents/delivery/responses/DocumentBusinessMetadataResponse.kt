package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentBusinessMetadata
import dev.fajar.hris.documents.domain.entities.DocumentCategory
import java.time.LocalDate

data class DocumentBusinessMetadataResponse(
    val category: DocumentCategory,
    val identityNumber: String?,
    val issuingCountry: String?,
    val issuedOn: LocalDate?,
    val validUntil: LocalDate?,
)

fun DocumentBusinessMetadata.toResponse() =
    DocumentBusinessMetadataResponse(category, identityNumber, issuingCountry, issuedOn, validUntil)
