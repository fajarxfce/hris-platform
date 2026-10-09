package dev.fajar.hris.communications.data.models

import java.time.OffsetDateTime
import java.util.UUID

data class InboxItemRow(
    val id: UUID,
    val announcementId: UUID,
    val publicationId: UUID,
    val version: Long,
    val title: String,
    val body: String,
    val acknowledgementRequired: Boolean,
    val deliveredAt: OffsetDateTime,
    val readAt: OffsetDateTime?,
    val acknowledgedAt: OffsetDateTime?,
)
