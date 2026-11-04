package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpacePushBindingsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationSpacePushBindingDataSource {
    fun lock(spaceId: UUID, deviceId: UUID)

    fun find(spaceId: UUID, accountId: UUID, deviceId: UUID): CollaborationSpacePushBindingsRecord?

    fun byKey(hash: String): CollaborationSpacePushBindingsRecord?

    fun insert(record: CollaborationSpacePushBindingsRecord)

    fun publish(expected: CollaborationSpacePushBindingsRecord, at: OffsetDateTime): Boolean
}
