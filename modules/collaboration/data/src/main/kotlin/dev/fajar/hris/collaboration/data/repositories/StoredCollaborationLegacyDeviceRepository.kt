package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationLegacyDeviceDataSource
import dev.fajar.hris.collaboration.data.mappers.legacyDeviceFromRow
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationLegacyDeviceRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID

class StoredCollaborationLegacyDeviceRepository(
    private val source: CollaborationLegacyDeviceDataSource
) : CollaborationLegacyDeviceRepository {
    override fun list(
        spaceId: UUID,
        accountId: UUID,
        after: CollaborationLegacyDevicePosition?,
        limit: Int,
    ): Result<CollaborationLegacyDevicePage> {
        if (limit !in 1..50)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_legacy_query")
            )
        return safeDatabaseCall {
            val rows = source.list(spaceId, accountId, after?.companyId, after?.deviceId, limit + 1)
            require(rows.size <= limit + 1)
            val mapped = rows.map { legacyDeviceFromRow(it, spaceId, accountId) }
            fun positionKey(position: CollaborationLegacyDevicePosition) =
                position.companyId.toString() + ":" + position.deviceId.toString()
            require(
                mapped.zipWithNext().all { (a, b) ->
                    positionKey(a.position) < positionKey(b.position)
                }
            )
            require(after == null || mapped.all { positionKey(it.position) > positionKey(after) })
            CollaborationLegacyDevicePage(
                mapped.take(limit),
                if (mapped.size > limit) mapped[limit - 1].position else null,
            )
        }
    }
}
