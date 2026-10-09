package dev.fajar.hris.communications.data.models

import java.time.OffsetDateTime
import java.util.UUID

data class InboxSummaryRow(
    val id: UUID,
    val announcementId: UUID,
    val version: Long,
    val title: String,
    val acknowledgementRequired: Boolean,
    val deliveredAt: OffsetDateTime,
    val readAt: OffsetDateTime?,
    val acknowledgedAt: OffsetDateTime?,
)
