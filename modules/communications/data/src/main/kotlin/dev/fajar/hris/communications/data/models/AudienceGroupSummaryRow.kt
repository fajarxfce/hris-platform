package dev.fajar.hris.communications.data.models

import java.time.OffsetDateTime
import java.util.UUID

data class AudienceGroupSummaryRow(
    val id: UUID,
    val version: Long,
    val name: String,
    val active: Boolean,
    val memberCount: Int,
    val recordedAt: OffsetDateTime,
)
