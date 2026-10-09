package dev.fajar.hris.documents.domain.entities

import java.util.UUID

data class DocumentRetentionState(
    val documentId: UUID,
    val version: Long? = null,
    val archive: DocumentArchive? = null,
    val legalHold: Boolean = false,
)
