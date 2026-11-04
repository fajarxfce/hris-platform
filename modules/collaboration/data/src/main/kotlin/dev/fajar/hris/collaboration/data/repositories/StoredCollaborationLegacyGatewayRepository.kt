package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.mappers.legacyGatewayFromRow
import dev.fajar.hris.collaboration.domain.entities.CollaborationLegacyGateway
import dev.fajar.hris.collaboration.domain.entities.CollaborationLegacyRuntimeOwner
import dev.fajar.hris.collaboration.domain.repositories.CollaborationLegacyGatewayRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import java.util.UUID

class StoredCollaborationLegacyGatewayRepository(
    private val source: CollaborationLegacyGatewayDataSource
) : CollaborationLegacyGatewayRepository {
    override fun find(
        spaceId: UUID,
        owner: CollaborationLegacyRuntimeOwner,
    ): Result<CollaborationLegacyGateway?> = safeDatabaseCall {
        source
            .find(
                spaceId,
                CollaborationLegacyRuntimeCoordinatesData(
                    owner.companyId,
                    owner.deviceId,
                    owner.accountId,
                    owner.nativeSessionId,
                ),
            )
            ?.let { legacyGatewayFromRow(it, spaceId, owner) }
    }
}
