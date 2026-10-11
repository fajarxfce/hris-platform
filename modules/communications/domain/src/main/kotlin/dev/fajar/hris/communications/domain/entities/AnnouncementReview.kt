package dev.fajar.hris.communications.domain.entities

import java.time.Instant

data class AnnouncementReview(
    val announcement: Announcement,
    val publicationJob: AnnouncementPublicationJob?,
    val availableActions: Set<AnnouncementAction>,
    val evaluatedAt: Instant,
)
