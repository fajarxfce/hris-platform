package dev.fajar.hris.communications.delivery.responses

import java.time.Instant
import java.util.UUID

data class AnnouncementDeliveryResponse(
    val announcementId: UUID,
    val recipientCount: Int,
    val pending: Int,
    val processing: Int,
    val completed: Int,
    val failed: Int,
    val superseded: Int,
    val unavailableRecipients: Int,
    val processedDevices: Int,
    val acceptedDevices: Int,
    val rejectedDevices: Int,
    val observedAt: Instant,
)
