package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

data class Announcement(
    val id: UUID,
    val version: Long,
    val title: String,
    val body: String,
    val audience: AnnouncementAudience,
    val acknowledgementRequired: Boolean,
    val status: AnnouncementStatus,
    val recordedAt: Instant,
    val recordedBy: UUID,
    val reason: String,
)
