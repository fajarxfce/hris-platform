package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationDeviceKeyPublicationsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationDeviceKeyDataSource {
    fun lock(companyId: UUID, deviceId: UUID)

    fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationDeviceKeyPublicationsRecord?

    fun insert(record: CollaborationDeviceKeyPublicationsRecord)

    fun publish(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        fingerprint: String,
        reservedAt: OffsetDateTime,
        publishedAt: OffsetDateTime,
    ): Boolean
}
