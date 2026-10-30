package dev.fajar.hris.communications.delivery.responses

data class AnnouncementEmailDeliveryResponse(
    val pending: Int,
    val processing: Int,
    val accepted: Int,
    val failed: Int,
    val superseded: Int,
    val unavailableRecipients: Int,
)
