package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceDeviceSessionsRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceDevicesRecord
import java.time.Instant
import java.util.UUID

interface CollaborationSpaceDeviceDataSource {
    fun findBound(
        spaceId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
    ): CollaborationSpaceDevicesRecord?

    /** Null means unavailable scope in the same snapshot, never an empty quota. */
    fun otherActiveParents(spaceId: UUID, accountId: UUID, excluding: UUID, at: Instant): Int?

    fun insertDevice(row: CollaborationSpaceDevicesRecord)

    fun insertBinding(row: CollaborationSpaceDeviceSessionsRecord)
}
