package dev.fajar.hris.communications.delivery.requests

import java.util.UUID

data class SaveAudienceGroupRequest(
    val name: String,
    val active: Boolean = true,
    val employmentIds: List<UUID> = emptyList(),
    val expectedVersion: Long? = null,
    val reason: String,
)
