package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeLeasesRecord
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeRecord
import java.time.OffsetDateTime
import java.util.UUID

data class CollaborationLegacyRuntimeCoordinatesData(
    val companyId: UUID,
    val deviceId: UUID,
    val accountId: UUID,
    val nativeSessionId: UUID,
)

interface CollaborationLegacyRuntimeDataSource {
    fun insertIfAbsent(row: CollaborationLegacyRuntimeRecord)

    fun find(
        owner: CollaborationLegacyRuntimeCoordinatesData,
        lock: Boolean,
    ): CollaborationLegacyRuntimeRecord?

    fun lease(
        owner: CollaborationLegacyRuntimeCoordinatesData,
        slot: Int,
        lock: Boolean,
    ): CollaborationLegacyRuntimeLeasesRecord?

    fun slots(
        owner: CollaborationLegacyRuntimeCoordinatesData
    ): List<CollaborationLegacyRuntimeLeasesRecord>

    fun claim(
        owner: CollaborationLegacyRuntimeCoordinatesData,
        until: OffsetDateTime,
    ): List<CollaborationLegacyRuntimeLeasesRecord>

    fun release(row: CollaborationLegacyRuntimeLeasesRecord): Boolean

    fun freeze(row: CollaborationLegacyRuntimeRecord, expectedVersion: Long): Boolean
}
