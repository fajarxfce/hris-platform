package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationDirectRoomBindingsRecord
import dev.fajar.hris.schema.tables.records.CollaborationDirectRoomProvisioningRecord
import java.util.UUID

interface CollaborationDirectRoomDataSource {
    fun lock(companyId: UUID, conversationId: UUID)

    fun provisioning(
        companyId: UUID,
        conversationId: UUID,
    ): CollaborationDirectRoomProvisioningRecord?

    fun binding(companyId: UUID, conversationId: UUID): CollaborationDirectRoomBindingsRecord?

    fun insert(record: CollaborationDirectRoomProvisioningRecord)

    fun replace(record: CollaborationDirectRoomProvisioningRecord, expectedVersion: Long): Boolean

    fun publish(record: CollaborationDirectRoomBindingsRecord)
}
