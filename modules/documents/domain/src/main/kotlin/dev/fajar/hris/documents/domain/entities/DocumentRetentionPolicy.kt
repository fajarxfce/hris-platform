package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

data class DocumentRetentionPolicy(
    val id: UUID,
    val classification: DocumentClassification,
    val version: Long,
    val retentionDays: Int?,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
