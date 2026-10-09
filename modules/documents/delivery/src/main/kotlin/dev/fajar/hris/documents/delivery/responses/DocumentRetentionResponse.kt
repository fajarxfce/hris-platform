package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentRetentionState
import java.util.UUID

data class DocumentRetentionResponse(
    val documentId: UUID,
    val version: Long?,
    val archive: DocumentArchiveResponse?,
    val legalHold: Boolean,
)

fun DocumentRetentionState.toResponse() =
    DocumentRetentionResponse(documentId, version, archive?.toResponse(), legalHold)
