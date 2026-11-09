package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceReadinessDeliveriesRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceReadinessTargetsRecord
import java.time.OffsetDateTime
import java.util.UUID

data class CollaborationSpaceReadinessLeaseData(
    val eventId: UUID,
    val channel: String,
    val spaceId: UUID,
    val publisherAccountId: UUID,
    val publisherDeviceId: UUID,
    val ownerId: UUID,
    val token: UUID,
    val until: OffsetDateTime,
)

data class CollaborationSpaceReadinessCheckpointData(
    val lease: CollaborationSpaceReadinessLeaseData,
    val revision: Long,
    val cutoff: OffsetDateTime,
    val capturedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
    val availableAt: OffsetDateTime,
    val attempts: Int,
    val afterAccountId: UUID?,
    val afterDeviceId: UUID?,
)

interface CollaborationSpaceReadinessQueueDataSource {
    fun claim(
        channel: String,
        owner: UUID,
        limit: Int,
    ): List<CollaborationSpaceReadinessDeliveriesRecord>

    fun find(
        lease: CollaborationSpaceReadinessLeaseData,
        lock: Boolean,
    ): CollaborationSpaceReadinessDeliveriesRecord?

    fun read(eventId: UUID, channel: String): CollaborationSpaceReadinessDeliveriesRecord?

    fun targets(
        checkpoint: CollaborationSpaceReadinessCheckpointData,
        limit: Int,
    ): List<CollaborationSpaceReadinessTargetsRecord>

    fun advance(
        checkpoint: CollaborationSpaceReadinessCheckpointData,
        accountId: UUID,
        deviceId: UUID,
    ): Boolean

    fun release(
        checkpoint: CollaborationSpaceReadinessCheckpointData,
        complete: Boolean,
        availableAt: OffsetDateTime,
    ): Boolean
}
