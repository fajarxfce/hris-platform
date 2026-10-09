package dev.fajar.hris.communications.delivery.requests

data class SaveAnnouncementRequest(
    val title: String,
    val body: String,
    val audience: AnnouncementAudienceRequest,
    val acknowledgementRequired: Boolean = false,
    val expectedVersion: Long? = null,
    val reason: String,
)
