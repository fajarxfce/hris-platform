package dev.fajar.hris.administration.delivery.responses

import dev.fajar.hris.administration.domain.entities.WorkerIntegrationRegistry
import dev.fajar.hris.administration.domain.policies.workerIntegrationIsRecent
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

data class WorkerIntegrationRegistryResponse(
    val observedAt: Instant,
    @get:ArraySchema(maxItems = 64, uniqueItems = true)
    val workers: List<WorkerIntegrationObservationResponse>,
)

data class WorkerIntegrationObservationResponse(
    val instanceId: UUID,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    @get:Schema(allowableValues = ["RECENT", "STALE"]) val status: String,
    @get:ArraySchema(minItems = 4, maxItems = 4, uniqueItems = true)
    val integrations: List<IntegrationConfigurationEntryResponse>,
)

fun WorkerIntegrationRegistry.toResponse() =
    WorkerIntegrationRegistryResponse(
        observedAt,
        workers.map { worker ->
            WorkerIntegrationObservationResponse(
                worker.instanceId,
                worker.firstSeenAt,
                worker.lastSeenAt,
                if (workerIntegrationIsRecent(worker, observedAt)) "RECENT" else "STALE",
                listOf(
                    IntegrationConfigurationEntryResponse("MAIL", worker.flags.mail),
                    IntegrationConfigurationEntryResponse("PUSH", worker.flags.push),
                    IntegrationConfigurationEntryResponse("STORAGE", worker.flags.storage),
                    IntegrationConfigurationEntryResponse("DOCUMENT_SCANNER", worker.flags.scanner),
                ),
            )
        },
    )
