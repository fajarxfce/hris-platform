package dev.fajar.hris.communications.delivery.mappers

import dev.fajar.hris.communications.delivery.responses.AnnouncementDeliveryResponse
import dev.fajar.hris.communications.domain.entities.AnnouncementDelivery

fun AnnouncementDelivery.toResponse() =
    AnnouncementDeliveryResponse(
        announcementId,
        recipientCount,
        counts.pending,
        counts.processing,
        counts.completed,
        counts.failed,
        counts.superseded,
        unavailableRecipients,
        counts.processedDevices,
        counts.acceptedDevices,
        counts.rejectedDevices,
        observedAt,
    )
