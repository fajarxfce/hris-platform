package dev.fajar.hris.administration.domain.entities

import java.time.Instant
import java.util.UUID

data class WorkerIntegrationFlags(
    val mail: Boolean,
    val push: Boolean,
    val storage: Boolean,
    val scanner: Boolean,
)

data class WorkerIntegrationObservation(
    val instanceId: UUID,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val flags: WorkerIntegrationFlags,
)

data class WorkerIntegrationRegistry(
    val observedAt: Instant,
    val workers: List<WorkerIntegrationObservation>,
)
