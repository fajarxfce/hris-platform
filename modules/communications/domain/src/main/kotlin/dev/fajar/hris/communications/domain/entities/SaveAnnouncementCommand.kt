package dev.fajar.hris.communications.domain.entities

import java.util.UUID

data class SaveAnnouncementCommand(
    val id: UUID,
    val expectedVersion: Long?,
    val title: String,
    val body: String,
    val audience: AnnouncementAudience,
    val acknowledgementRequired: Boolean,
    val reason: String,
)
