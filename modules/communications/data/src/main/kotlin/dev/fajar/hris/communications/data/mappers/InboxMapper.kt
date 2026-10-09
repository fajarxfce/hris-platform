package dev.fajar.hris.communications.data.mappers

import dev.fajar.hris.communications.data.models.*
import dev.fajar.hris.communications.domain.entities.*

fun InboxItemRow.toItem() =
    InboxItem(
        id,
        announcementId,
        publicationId,
        version,
        title,
        body,
        acknowledgementRequired,
        deliveredAt.toInstant(),
        readAt?.toInstant(),
        acknowledgedAt?.toInstant(),
    )

fun InboxSummaryRow.toSummary() =
    InboxSummary(
        id,
        announcementId,
        version,
        title,
        acknowledgementRequired,
        deliveredAt.toInstant(),
        readAt?.toInstant(),
        acknowledgedAt?.toInstant(),
    )
