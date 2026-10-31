package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationRecipientDevicesRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationRecipientDataSource {
    fun devices(
        companyId: UUID,
        accountIds: Set<UUID>,
        at: OffsetDateTime,
        limit: Int,
    ): List<CollaborationRecipientDevicesRecord>
}
