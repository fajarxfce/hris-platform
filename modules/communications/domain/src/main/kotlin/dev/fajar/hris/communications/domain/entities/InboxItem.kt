package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

data class InboxItem(
    val id: UUID,
    val announcementId: UUID,
    val publicationId: UUID,
    val version: Long,
    val title: String,
    val body: String,
    val acknowledgementRequired: Boolean,
    val deliveredAt: Instant,
    val readAt: Instant?,
    val acknowledgedAt: Instant?,
)
