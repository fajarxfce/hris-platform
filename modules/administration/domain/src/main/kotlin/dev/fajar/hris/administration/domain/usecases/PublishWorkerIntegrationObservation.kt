package dev.fajar.hris.administration.domain.usecases

import dev.fajar.hris.administration.domain.entities.IntegrationKind
import dev.fajar.hris.administration.domain.entities.WorkerIntegrationFlags
import dev.fajar.hris.administration.domain.policies.MAX_WORKER_INTEGRATION_OBSERVATIONS
import dev.fajar.hris.administration.domain.policies.WORKER_INTEGRATION_RETENTION
import dev.fajar.hris.administration.domain.repositories.IntegrationConfigurationRepository
import dev.fajar.hris.administration.domain.repositories.WorkerIntegrationRepository
import dev.fajar.hris.core.domain.*
import java.time.Clock
import java.util.UUID

/** Worker-only; the database role owns publication, not an interactive account. */
class PublishWorkerIntegrationObservation(
    private val configuration: IntegrationConfigurationRepository,
    private val registry: WorkerIntegrationRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(instanceId: UUID): Result<Unit> {
        val configured = configuration.read()
        if (configured is Result.Failed) return configured
        val enabled =
            (configured as Result.Success).value.filter { it.enabled }.map { it.kind }.toSet()
        val flags =
            WorkerIntegrationFlags(
                IntegrationKind.MAIL in enabled,
                IntegrationKind.PUSH in enabled,
                IntegrationKind.STORAGE in enabled,
                IntegrationKind.DOCUMENT_SCANNER in enabled,
            )
        val worker = Actor(UUID(0, 0), null, emptySet(), clock.instant(), UUID.randomUUID())
        return transactions.run(worker) {
            val locked = registry.lockRegistry()
            if (locked is Result.Failed) return@run locked
            val read = registry.read()
            if (read is Result.Failed) return@run read
            val snapshot = (read as Result.Success).value
            val cutoff = snapshot.observedAt.minus(WORKER_INTEGRATION_RETENTION)
            val retained = snapshot.workers.filter { !it.lastSeenAt.isBefore(cutoff) }
            if (
                retained.none { it.instanceId == instanceId } &&
                    retained.size >= MAX_WORKER_INTEGRATION_OBSERVATIONS
            )
                return@run Result.Failed(
                    Failure(FailureKind.UNAVAILABLE, "worker_observation_capacity")
                )
            val expired = registry.expire(cutoff)
            if (expired is Result.Failed) return@run expired
            registry.publish(instanceId, flags).flatMap { published ->
                if (published) Result.Success(Unit)
                else Result.Failed(Failure(FailureKind.CONFLICT, "worker_configuration_changed"))
            }
        }
    }
}
