package dev.fajar.hris.communications.domain.entities

import java.time.Instant
import java.util.UUID

/** Service acceptance is not evidence of receipt or display on a device. */
data class AnnouncementDeliveryCounts(
    val pending: Int,
    val processing: Int,
    val completed: Int,
    val failed: Int,
    val superseded: Int,
    val processedDevices: Int,
    val acceptedDevices: Int,
    val rejectedDevices: Int,
) {
    val trackedRecipients: Int
        get() = pending + processing + completed + failed + superseded
}

data class AnnouncementDelivery(
    val announcementId: UUID,
    val recipientCount: Int,
    val counts: AnnouncementDeliveryCounts,
    val observedAt: Instant,
) {
    val unavailableRecipients: Int
        get() = recipientCount - counts.trackedRecipients
}
