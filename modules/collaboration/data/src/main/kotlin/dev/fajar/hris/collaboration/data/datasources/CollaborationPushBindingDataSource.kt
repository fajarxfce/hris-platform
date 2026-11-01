package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationPushBindingsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationPushBindingDataSource {
    fun lock(companyId: UUID, deviceId: UUID)

    fun find(companyId: UUID, accountId: UUID, deviceId: UUID): CollaborationPushBindingsRecord?

    fun byKey(hash: String): CollaborationPushBindingsRecord?

    fun insert(record: CollaborationPushBindingsRecord)

    fun publish(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        parentId: UUID,
        reservedAt: OffsetDateTime,
        at: OffsetDateTime,
    ): Boolean
}
