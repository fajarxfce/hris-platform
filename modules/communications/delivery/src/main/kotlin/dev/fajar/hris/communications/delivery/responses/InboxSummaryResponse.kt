package dev.fajar.hris.communications.delivery.responses

import java.util.UUID

data class InboxSummaryResponse(
    val id: UUID,
    val announcementId: UUID,
    val version: Long,
    val title: String,
    val acknowledgementRequired: Boolean,
    val deliveredAt: String,
    val readAt: String?,
    val acknowledgedAt: String?,
)
