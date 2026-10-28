package dev.fajar.hris.administration.data.repositories

import dev.fajar.hris.administration.data.datasources.WorkerIntegrationDataSource
import dev.fajar.hris.administration.data.dto.WorkerIntegrationWriteRow
import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.MAX_WORKER_INTEGRATION_OBSERVATIONS
import dev.fajar.hris.administration.domain.repositories.WorkerIntegrationRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.UUID

class StoredWorkerIntegrationRepository(private val source: WorkerIntegrationDataSource) :
    WorkerIntegrationRepository {
    override fun lockRegistry() = safeDatabaseCall { source.lockRegistry() }

    override fun read() = safeDatabaseCall {
        val rows = source.read(MAX_WORKER_INTEGRATION_OBSERVATIONS + 1)
        check(rows.isNotEmpty() && rows.size <= MAX_WORKER_INTEGRATION_OBSERVATIONS)
        val at = rows.first().observedAt.toInstant()
        check(rows.all { it.observedAt.toInstant() == at })
        val workers =
            rows.mapNotNull { row ->
                if (row.instanceId == null) {
                    check(
                        rows.size == 1 &&
                            row.firstSeenAt == null &&
                            row.lastSeenAt == null &&
                            row.mail == null &&
                            row.push == null &&
                            row.storage == null &&
                            row.scanner == null
                    )
                    null
                } else {
                    val first = requireNotNull(row.firstSeenAt).toInstant()
                    val last = requireNotNull(row.lastSeenAt).toInstant()
                    check(!last.isBefore(first))
                    WorkerIntegrationObservation(
                        row.instanceId,
                        first,
                        last,
                        WorkerIntegrationFlags(
                            requireNotNull(row.mail),
                            requireNotNull(row.push),
                            requireNotNull(row.storage),
                            requireNotNull(row.scanner),
                        ),
                    )
                }
            }
        check(workers.map { it.instanceId }.toSet().size == workers.size)
        WorkerIntegrationRegistry(at, Collections.unmodifiableList(workers))
    }

    override fun expire(before: Instant) = safeDatabaseCall {
        source.expire(before.atOffset(ZoneOffset.UTC)).also {
            check(it in 0..MAX_WORKER_INTEGRATION_OBSERVATIONS)
        }
    }

    override fun publish(instanceId: UUID, flags: WorkerIntegrationFlags) = safeDatabaseCall {
        source
            .publish(
                WorkerIntegrationWriteRow(
                    instanceId,
                    flags.mail,
                    flags.push,
                    flags.storage,
                    flags.scanner,
                )
            )
            .also { check(it in 0..1) } == 1
    }
}
