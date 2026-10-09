package dev.fajar.hris.communications.delivery.mappers

import dev.fajar.hris.communications.delivery.responses.AnnouncementAudiencePreviewResponse
import dev.fajar.hris.communications.domain.entities.AnnouncementAudiencePreview

fun AnnouncementAudiencePreview.toResponse() =
    AnnouncementAudiencePreviewResponse(
        announcementId,
        version,
        asOfDate,
        evaluatedAt,
        recipientCount,
        audienceVersions,
    )
