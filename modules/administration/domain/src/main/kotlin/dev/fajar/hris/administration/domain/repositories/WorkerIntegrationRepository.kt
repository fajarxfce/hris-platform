package dev.fajar.hris.administration.domain.repositories

import dev.fajar.hris.administration.domain.entities.WorkerIntegrationFlags
import dev.fajar.hris.administration.domain.entities.WorkerIntegrationRegistry
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

interface WorkerIntegrationRepository {
    fun lockRegistry(): Result<Unit>

    fun read(): Result<WorkerIntegrationRegistry>

    fun expire(before: Instant): Result<Int>

    fun publish(instanceId: UUID, flags: WorkerIntegrationFlags): Result<Boolean>
}
