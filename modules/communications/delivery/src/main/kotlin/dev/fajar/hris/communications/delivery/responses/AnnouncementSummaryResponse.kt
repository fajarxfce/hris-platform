package dev.fajar.hris.communications.delivery.responses

import java.util.UUID

data class AnnouncementSummaryResponse(
    val id: UUID,
    val version: Long,
    val title: String,
    val audienceKind: String,
    val targetCount: Int,
    val acknowledgementRequired: Boolean,
    val status: String,
    val recordedAt: String,
    val publicationJobId: UUID?,
    val scheduledFor: String?,
    val publishedAt: String?,
    val recipientCount: Int,
    val publicationAttempts: Int,
)
