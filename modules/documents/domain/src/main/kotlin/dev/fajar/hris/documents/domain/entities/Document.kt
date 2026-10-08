package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

data class Document(
    val id: UUID,
    val employmentId: UUID,
    val title: String,
    val classification: DocumentClassification,
    val revisionCount: Int,
    val version: Long,
    val createdBy: UUID,
    val createdAt: Instant,
    val currentRevisionId: UUID? = null,
)
