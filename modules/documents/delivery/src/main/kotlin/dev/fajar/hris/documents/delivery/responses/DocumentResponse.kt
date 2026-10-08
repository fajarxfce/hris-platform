package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.Document
import java.time.Instant
import java.util.UUID

data class DocumentResponse(
    val id: UUID,
    val employmentId: UUID,
    val title: String,
    val classification: String,
    val revisionCount: Int,
    val version: Long,
    val createdBy: UUID,
    val createdAt: Instant,
)

fun Document.toResponse() =
    DocumentResponse(
        id,
        employmentId,
        title,
        classification.name,
        revisionCount,
        version,
        createdBy,
        createdAt,
    )
