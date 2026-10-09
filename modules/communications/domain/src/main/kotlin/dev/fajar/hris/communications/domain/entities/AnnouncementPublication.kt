package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

data class AnnouncementPublication(
    val id: UUID,
    val announcementId: UUID,
    val contentVersion: Long,
    val publishedAt: Instant,
    val publishedBy: UUID,
    val recipients: List<AnnouncementRecipient>,
    val audienceVersions: Map<UUID, Long>,
)
