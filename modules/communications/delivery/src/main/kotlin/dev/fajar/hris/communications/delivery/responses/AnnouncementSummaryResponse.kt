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
)
