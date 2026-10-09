package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

data class AnnouncementSummary(
    val id: UUID,
    val version: Long,
    val title: String,
    val audienceKind: AudienceKind,
    val targetCount: Int,
    val acknowledgementRequired: Boolean,
    val status: AnnouncementStatus,
    val recordedAt: Instant,
)
