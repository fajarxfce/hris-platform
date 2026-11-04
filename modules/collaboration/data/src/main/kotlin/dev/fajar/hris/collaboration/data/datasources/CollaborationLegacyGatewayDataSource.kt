package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyGatewaysRecord
import java.util.UUID

interface CollaborationLegacyGatewayDataSource {
    fun find(
        spaceId: UUID,
        owner: CollaborationLegacyRuntimeCoordinatesData,
    ): CollaborationSpaceLegacyGatewaysRecord?
}
