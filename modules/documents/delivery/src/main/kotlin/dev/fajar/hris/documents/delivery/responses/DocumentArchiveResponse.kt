package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentArchive
import java.time.Instant
import java.util.UUID

data class DocumentArchiveResponse(
    val archivedAt: Instant,
    val policyId: UUID?,
    val policyVersion: Long?,
    val retentionDays: Int?,
    val eligibleAt: Instant?,
)

fun DocumentArchive.toResponse() =
    DocumentArchiveResponse(archivedAt, policyId, policyVersion, retentionDays, eligibleAt)
