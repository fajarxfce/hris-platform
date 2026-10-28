package dev.fajar.hris.administration.data.dto

import java.time.OffsetDateTime
import java.util.UUID

data class WorkerIntegrationRow(
    val observedAt: OffsetDateTime,
    val instanceId: UUID?,
    val firstSeenAt: OffsetDateTime?,
    val lastSeenAt: OffsetDateTime?,
    val mail: Boolean?,
    val push: Boolean?,
    val storage: Boolean?,
    val scanner: Boolean?,
)

data class WorkerIntegrationWriteRow(
    val instanceId: UUID,
    val mail: Boolean,
    val push: Boolean,
    val storage: Boolean,
    val scanner: Boolean,
)
