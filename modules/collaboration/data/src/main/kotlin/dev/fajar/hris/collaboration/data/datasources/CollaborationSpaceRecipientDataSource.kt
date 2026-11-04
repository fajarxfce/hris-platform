package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceRecipientDevicesRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationSpaceRecipientDataSource {
    fun devices(
        spaceId: UUID,
        accountIds: Set<UUID>,
        at: OffsetDateTime,
        limit: Int,
    ): List<CollaborationSpaceRecipientDevicesRecord>
}
