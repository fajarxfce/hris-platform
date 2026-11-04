package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceGatewaySessionsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationSpaceGatewaySessionDataSource {
    fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): CollaborationSpaceGatewaySessionsRecord?

    fun byAccess(hash: String): CollaborationSpaceGatewaySessionsRecord?

    fun byRefresh(hash: String): CollaborationSpaceGatewaySessionsRecord?

    fun insert(record: CollaborationSpaceGatewaySessionsRecord)

    fun replace(
        record: CollaborationSpaceGatewaySessionsRecord,
        expectedVersion: Long,
        expectedRefreshHash: String,
    ): Boolean

    fun retire(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: OffsetDateTime,
    ): Boolean

    fun clearExpiredReplays(at: OffsetDateTime, limit: Int): Int
}
