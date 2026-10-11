package dev.fajar.hris.communications.delivery.responses

import dev.fajar.hris.communications.domain.entities.AnnouncementAction
import java.time.Instant

data class AnnouncementReviewResponse(
    val announcement: AnnouncementResponse,
    val publicationJob: AnnouncementPublicationJobResponse?,
    val availableActions: Set<AnnouncementAction>,
    val evaluatedAt: Instant,
)
