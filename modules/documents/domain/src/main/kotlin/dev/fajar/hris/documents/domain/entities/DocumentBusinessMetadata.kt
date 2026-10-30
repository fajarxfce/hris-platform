package dev.fajar.hris.documents.domain.entities

import java.time.LocalDate

data class DocumentBusinessMetadata(
    val category: DocumentCategory = DocumentCategory.OTHER,
    val identityNumber: String? = null,
    val issuingCountry: String? = null,
    val issuedOn: LocalDate? = null,
    val validUntil: LocalDate? = null,
)
