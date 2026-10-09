package dev.fajar.hris.communications.delivery.requests

import java.time.Instant

data class PublishAnnouncementRequest(
    val expectedVersion: Long,
    val scheduledFor: Instant? = null,
    val reason: String,
)
