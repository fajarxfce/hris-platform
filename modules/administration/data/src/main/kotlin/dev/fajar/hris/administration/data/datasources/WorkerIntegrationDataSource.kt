package dev.fajar.hris.administration.data.datasources

import dev.fajar.hris.administration.data.dto.WorkerIntegrationRow
import dev.fajar.hris.administration.data.dto.WorkerIntegrationWriteRow
import java.time.OffsetDateTime

interface WorkerIntegrationDataSource {
    fun lockRegistry()

    fun read(limit: Int): List<WorkerIntegrationRow>

    fun expire(before: OffsetDateTime): Int

    fun publish(row: WorkerIntegrationWriteRow): Int
}
