package dev.fajar.hris.storage.data.datasources

import dev.fajar.hris.schema.tables.records.ObjectCleanupQueueRecord
import java.time.OffsetDateTime
import java.util.UUID

interface ObjectCleanupDataSource {
    fun insert(row: ObjectCleanupQueueRecord)

    fun bytes(companyId: UUID): Long

    fun retain(companyId: UUID, ids: Set<UUID>): Int

    fun advance(companyId: UUID, resourceId: UUID, at: OffsetDateTime)

    fun claim(owner: UUID, limit: Int, seconds: Int, attempts: Int): List<ObjectCleanupQueueRecord>

    fun exhausted(attempts: Int, limit: Int): List<ObjectCleanupQueueRecord>

    fun failExpired(id: UUID, token: UUID): Int

    fun complete(id: UUID, token: UUID): Int

    fun fail(id: UUID, token: UUID, code: String, status: String, at: OffsetDateTime): Int

    fun find(companyId: UUID, id: UUID): ObjectCleanupQueueRecord?

    fun list(companyId: UUID, after: UUID?, limit: Int): List<ObjectCleanupQueueRecord>

    fun retry(companyId: UUID, id: UUID, version: Long, at: OffsetDateTime): Long?
}
