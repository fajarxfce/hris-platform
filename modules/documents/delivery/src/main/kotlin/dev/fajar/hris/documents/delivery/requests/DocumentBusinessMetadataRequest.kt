package dev.fajar.hris.documents.delivery.requests

import dev.fajar.hris.documents.domain.entities.DocumentBusinessMetadata
import dev.fajar.hris.documents.domain.entities.DocumentCategory
import java.time.LocalDate

data class DocumentBusinessMetadataRequest(
    val category: DocumentCategory,
    val identityNumber: String? = null,
    val issuingCountry: String? = null,
    val issuedOn: LocalDate? = null,
    val validUntil: LocalDate? = null,
)

fun DocumentBusinessMetadataRequest.toDomain() =
    DocumentBusinessMetadata(category, identityNumber, issuingCountry, issuedOn, validUntil)
