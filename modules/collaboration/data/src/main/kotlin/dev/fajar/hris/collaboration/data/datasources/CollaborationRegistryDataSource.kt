package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.collaboration.data.models.BoundCollaborationDeviceData
import dev.fajar.hris.schema.tables.records.CollaborationDeviceSessionsRecord
import dev.fajar.hris.schema.tables.records.CollaborationDevicesRecord
import java.time.Instant
import java.util.UUID

interface CollaborationRegistryDataSource {
    fun findBound(
        companyId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
    ): BoundCollaborationDeviceData?

    fun countActiveParentSessions(accountId: UUID, at: Instant): Int

    fun insertDevice(record: CollaborationDevicesRecord)

    fun insertBinding(record: CollaborationDeviceSessionsRecord)
}
