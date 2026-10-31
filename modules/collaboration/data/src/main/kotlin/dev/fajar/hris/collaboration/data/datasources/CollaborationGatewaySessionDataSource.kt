package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationGatewaySessionsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationGatewaySessionDataSource {
    fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): CollaborationGatewaySessionsRecord?

    fun byAccess(hash: String): CollaborationGatewaySessionsRecord?

    fun byRefresh(hash: String): CollaborationGatewaySessionsRecord?

    fun insert(record: CollaborationGatewaySessionsRecord)

    fun replace(
        record: CollaborationGatewaySessionsRecord,
        expectedVersion: Long,
        expectedRefreshHash: String,
    ): Boolean

    fun retire(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: OffsetDateTime,
    ): Boolean

    fun clearExpiredReplays(at: OffsetDateTime, limit: Int): Int
}
