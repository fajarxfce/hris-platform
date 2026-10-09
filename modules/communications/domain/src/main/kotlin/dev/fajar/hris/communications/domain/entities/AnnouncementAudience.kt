package dev.fajar.hris.communications.domain.entities

import java.util.UUID

data class AnnouncementAudience(val kind: AudienceKind, val targetIds: List<UUID> = emptyList())
