package dev.fajar.hris.communications.delivery.responses

import java.util.UUID

data class AudienceGroupResponse(
    val id: UUID,
    val version: Long,
    val name: String,
    val active: Boolean,
    val employmentIds: Set<UUID>,
    val recordedAt: String,
    val recordedBy: UUID,
    val reason: String,
)
