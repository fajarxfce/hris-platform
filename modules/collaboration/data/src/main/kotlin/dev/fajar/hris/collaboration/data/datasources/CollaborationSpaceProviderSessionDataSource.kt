package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceProviderSessionsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationSpaceProviderSessionDataSource {
    fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationSpaceProviderSessionsRecord?

    fun insert(row: CollaborationSpaceProviderSessionsRecord)

    fun replace(row: CollaborationSpaceProviderSessionsRecord, expectedGeneration: Long): Boolean

    fun clear(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedGeneration: Long,
        at: OffsetDateTime,
    ): Boolean
}
