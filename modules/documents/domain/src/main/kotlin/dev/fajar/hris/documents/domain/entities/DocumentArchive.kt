package dev.fajar.hris.documents.domain.entities

import java.time.Instant
import java.util.UUID

/** Frozen eligibility at archival. A null deadline means indefinite retention. */
data class DocumentArchive(
    val archivedAt: Instant,
    val policyId: UUID?,
    val policyVersion: Long?,
    val retentionDays: Int?,
    val eligibleAt: Instant?,
)
