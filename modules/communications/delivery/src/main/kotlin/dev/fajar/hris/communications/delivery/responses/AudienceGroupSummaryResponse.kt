package dev.fajar.hris.communications.delivery.responses

import java.util.UUID

data class AudienceGroupSummaryResponse(
    val id: UUID,
    val version: Long,
    val name: String,
    val active: Boolean,
    val memberCount: Int,
    val recordedAt: String,
)
