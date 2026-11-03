package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyParentsRecord
import java.util.UUID

interface CollaborationLegacyParentDataSource {
    fun find(
        spaceId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
        companyId: UUID,
        deviceId: UUID,
    ): CollaborationSpaceLegacyParentsRecord?
}
