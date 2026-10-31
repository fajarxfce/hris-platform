package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationDeviceProvisioningRecord
import java.util.UUID

interface CollaborationProvisioningDataSource {
    fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationDeviceProvisioningRecord?

    fun insert(record: CollaborationDeviceProvisioningRecord)

    fun replace(record: CollaborationDeviceProvisioningRecord, expectedVersion: Long): Boolean
}
