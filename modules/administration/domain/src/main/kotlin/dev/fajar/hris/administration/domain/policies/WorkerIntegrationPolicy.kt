package dev.fajar.hris.administration.domain.policies

import dev.fajar.hris.administration.domain.entities.WorkerIntegrationObservation
import java.time.Duration
import java.time.Instant

const val MAX_WORKER_INTEGRATION_OBSERVATIONS = 64
val WORKER_INTEGRATION_RETENTION: Duration = Duration.ofMinutes(5)
val WORKER_INTEGRATION_FRESHNESS: Duration = Duration.ofSeconds(90)

fun workerIntegrationIsRecent(observation: WorkerIntegrationObservation, at: Instant): Boolean =
    !observation.lastSeenAt.isAfter(at) &&
        observation.lastSeenAt.plus(WORKER_INTEGRATION_FRESHNESS).isAfter(at)
