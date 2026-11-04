package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceKeyPublicationsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationSpaceKeyDataSource {
    fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationSpaceKeyPublicationsRecord?

    fun insert(record: CollaborationSpaceKeyPublicationsRecord)

    fun publish(expected: CollaborationSpaceKeyPublicationsRecord, at: OffsetDateTime): Boolean
}
