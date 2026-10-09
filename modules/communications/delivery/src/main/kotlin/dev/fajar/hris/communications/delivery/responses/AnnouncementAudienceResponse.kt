package dev.fajar.hris.communications.delivery.responses

import java.util.UUID

data class AnnouncementAudienceResponse(val kind: String, val targetIds: List<UUID>)
