package dev.fajar.hris.communications.delivery.mappers

import dev.fajar.hris.communications.delivery.responses.*
import dev.fajar.hris.communications.domain.entities.*

fun InboxItem.toResponse() =
    InboxItemResponse(
        id,
        announcementId,
        publicationId,
        version,
        title,
        body,
        acknowledgementRequired,
        deliveredAt.toString(),
        readAt?.toString(),
        acknowledgedAt?.toString(),
    )

fun InboxSummary.toResponse() =
    InboxSummaryResponse(
        id,
        announcementId,
        version,
        title,
        acknowledgementRequired,
        deliveredAt.toString(),
        readAt?.toString(),
        acknowledgedAt?.toString(),
    )
