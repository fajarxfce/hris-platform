package dev.fajar.hris.communications.delivery.mappers

import dev.fajar.hris.communications.delivery.responses.*
import dev.fajar.hris.communications.domain.entities.*

fun Announcement.toResponse() =
    AnnouncementResponse(
        id,
        version,
        title,
        body,
        AnnouncementAudienceResponse(audience.kind.name, audience.targetIds),
        acknowledgementRequired,
        status.name,
        recordedAt.toString(),
        recordedBy,
        reason,
    )

fun AnnouncementSummary.toResponse() =
    AnnouncementSummaryResponse(
        id,
        version,
        title,
        audienceKind.name,
        targetCount,
        acknowledgementRequired,
        status.name,
        recordedAt.toString(),
    )
