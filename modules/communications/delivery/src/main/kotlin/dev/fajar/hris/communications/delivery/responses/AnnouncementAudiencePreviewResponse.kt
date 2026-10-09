package dev.fajar.hris.communications.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AnnouncementAudiencePreviewResponse(
    val announcementId: UUID,
    val version: Long,
    val asOfDate: LocalDate,
    val evaluatedAt: Instant,
    val recipientCount: Int,
    val audienceVersions: Map<UUID, Long>,
)
