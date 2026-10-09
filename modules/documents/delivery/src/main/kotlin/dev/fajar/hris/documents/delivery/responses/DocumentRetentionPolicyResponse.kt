package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentRetentionPolicy
import java.time.Instant
import java.util.UUID

data class DocumentRetentionPolicyResponse(
    val id: UUID,
    val classification: String,
    val version: Long,
    val retentionDays: Int?,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)

fun DocumentRetentionPolicy.toResponse() =
    DocumentRetentionPolicyResponse(
        id,
        classification.name,
        version,
        retentionDays,
        actorId,
        recordedAt,
        reason,
    )
