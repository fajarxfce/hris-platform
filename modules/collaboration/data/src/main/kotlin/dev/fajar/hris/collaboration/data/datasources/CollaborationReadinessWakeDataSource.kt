package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationReadinessTargetsRecord
import dev.fajar.hris.schema.tables.records.CollaborationReadinessWakesRecord
import java.time.OffsetDateTime
import java.util.UUID

data class CollaborationReadinessLeaseData(
    val eventId: UUID,
    val companyId: UUID,
    val publisherAccountId: UUID,
    val publisherDeviceId: UUID,
    val ownerId: UUID,
    val token: UUID,
    val until: OffsetDateTime,
)

data class CollaborationReadinessCheckpointData(
    val lease: CollaborationReadinessLeaseData,
    val publicationCutoff: OffsetDateTime,
    val afterAccountId: UUID?,
    val afterDeviceId: UUID?,
    val publicationRevision: Long? = null,
)

interface CollaborationReadinessWakeDataSource {
    fun claim(ownerId: UUID, limit: Int): List<CollaborationReadinessWakesRecord>

    fun find(
        lease: CollaborationReadinessLeaseData,
        lock: Boolean,
    ): CollaborationReadinessWakesRecord?

    fun targets(
        checkpoint: CollaborationReadinessCheckpointData,
        limit: Int,
    ): List<CollaborationReadinessTargetsRecord>

    fun advance(
        checkpoint: CollaborationReadinessCheckpointData,
        accountId: UUID,
        deviceId: UUID,
    ): Boolean

    fun release(
        checkpoint: CollaborationReadinessCheckpointData,
        complete: Boolean,
        availableAt: OffsetDateTime,
    ): Boolean
}
