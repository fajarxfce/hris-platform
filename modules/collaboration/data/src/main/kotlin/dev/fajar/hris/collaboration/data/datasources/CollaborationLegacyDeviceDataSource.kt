package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyDevicesRecord
import java.util.UUID

interface CollaborationLegacyDeviceDataSource {
    fun list(
        spaceId: UUID,
        accountId: UUID,
        afterCompanyId: UUID?,
        afterDeviceId: UUID?,
        limit: Int,
    ): List<CollaborationSpaceLegacyDevicesRecord>
}
