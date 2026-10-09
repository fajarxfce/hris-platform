package dev.fajar.hris.documents.domain.entities

import java.util.UUID

/** Identifies immutable business evidence; it is not a mutable UI attachment list. */
data class DocumentReferenceOrigin(
    val kind: DocumentReferenceKind,
    val resourceId: UUID,
    val version: Int,
)
