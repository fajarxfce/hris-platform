package dev.fajar.hris.communications.delivery.requests

import dev.fajar.hris.communications.domain.entities.AudienceKind
import java.util.UUID

data class AnnouncementAudienceRequest(
    val kind: AudienceKind,
    val targetIds: List<UUID> = emptyList(),
)
