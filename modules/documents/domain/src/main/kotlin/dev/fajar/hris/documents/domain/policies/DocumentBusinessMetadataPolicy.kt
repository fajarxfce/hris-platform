package dev.fajar.hris.documents.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import java.util.Locale

fun normalizeDocumentMetadata(input: DocumentBusinessMetadata): DocumentBusinessMetadata =
    input.copy(
        identityNumber = input.identityNumber?.trim()?.takeIf { it.isNotEmpty() },
        issuingCountry =
            input.issuingCountry?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() },
    )

fun validateDocumentMetadata(metadata: DocumentBusinessMetadata): Result<Unit> {
    val fields =
        buildMap<String, String> {
            if (
                metadata.identityNumber?.let { it.length > 120 || it.any(Char::isISOControl) } ==
                    true
            )
                put("metadata.identityNumber", "invalid")
            if (
                metadata.issuingCountry != null &&
                    metadata.issuingCountry !in Locale.getISOCountries()
            )
                put("metadata.issuingCountry", "invalid_country")
            if (metadata.issuedOn?.let { it.year !in 1900..2200 } == true)
                put("metadata.issuedOn", "invalid_date")
            if (metadata.validUntil?.let { it.year !in 1900..2200 } == true)
                put("metadata.validUntil", "invalid_date")
            if (
                metadata.issuedOn != null &&
                    metadata.validUntil != null &&
                    metadata.validUntil < metadata.issuedOn
            )
                put("metadata.validUntil", "before_issue_date")
        }
    return if (fields.isEmpty()) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_metadata", fields))
}

/** Appending nothing for an omitted value preserves existing upload receipt keys. */
fun documentMetadataKey(metadata: DocumentBusinessMetadata?): List<String?> =
    if (metadata == null) emptyList()
    else
        listOf(
            "document_metadata_v1",
            metadata.category.name,
            metadata.identityNumber,
            metadata.issuingCountry,
            metadata.issuedOn?.toString(),
            metadata.validUntil?.toString(),
        )
