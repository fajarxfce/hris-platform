package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceRequestLeasesRecord
import java.time.OffsetDateTime
import java.util.UUID

data class CollaborationSpaceRequestCoordinatesData(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
)

interface CollaborationSpaceRequestDataSource {
    fun claim(
        owner: CollaborationSpaceRequestCoordinatesData,
        kind: String,
        gatewayId: UUID?,
        gatewayVersion: Long?,
        until: OffsetDateTime,
    ): List<CollaborationSpaceRequestLeasesRecord>

    fun find(
        owner: CollaborationSpaceRequestCoordinatesData,
        slot: Int,
        lock: Boolean,
    ): CollaborationSpaceRequestLeasesRecord?

    fun release(lease: CollaborationSpaceRequestLeasesRecord): Boolean

    fun clearExpired(at: OffsetDateTime, limit: Int): Int
}
