package dev.fajar.hris.communications.data.models

import java.time.OffsetDateTime
import java.util.UUID

data class AnnouncementSummaryRow(
    val id: UUID,
    val version: Long,
    val title: String,
    val audienceKind: String,
    val targetCount: Int,
    val acknowledgementRequired: Boolean,
    val status: String,
    val recordedAt: OffsetDateTime,
    val publicationJobId: UUID?,
    val scheduledFor: OffsetDateTime?,
    val publishedAt: OffsetDateTime?,
    val recipientCount: Int,
    val publicationAttempts: Int,
)
