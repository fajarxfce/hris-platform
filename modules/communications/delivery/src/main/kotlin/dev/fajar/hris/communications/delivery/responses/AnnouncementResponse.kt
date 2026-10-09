package dev.fajar.hris.communications.delivery.responses

import java.util.UUID

data class AnnouncementResponse(
    val id: UUID,
    val version: Long,
    val title: String,
    val body: String,
    val audience: AnnouncementAudienceResponse,
    val acknowledgementRequired: Boolean,
    val status: String,
    val recordedAt: String,
    val recordedBy: UUID,
    val reason: String,
    val publicationJobId: UUID?,
    val scheduledFor: String?,
    val publishedAt: String?,
    val recipientCount: Int,
    val publicationAttempts: Int,
)
