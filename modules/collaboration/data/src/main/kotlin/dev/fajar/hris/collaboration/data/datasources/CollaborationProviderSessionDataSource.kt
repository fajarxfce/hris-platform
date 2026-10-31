package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationProviderSessionsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationProviderSessionDataSource {
    fun find(companyId: UUID, accountId: UUID, deviceId: UUID): CollaborationProviderSessionsRecord?

    fun insert(record: CollaborationProviderSessionsRecord)

    fun replace(record: CollaborationProviderSessionsRecord, expectedGeneration: Long): Boolean

    fun clear(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedGeneration: Long,
        at: OffsetDateTime,
    ): Boolean
}
