package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationReadinessControlTargetsRecord
import dev.fajar.hris.schema.tables.records.CollaborationReadinessControlsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface CollaborationReadinessControlQueueDataSource {
    fun claim(ownerId: UUID, limit: Int): List<CollaborationReadinessControlsRecord>

    fun find(
        lease: CollaborationReadinessLeaseData,
        lock: Boolean,
    ): CollaborationReadinessControlsRecord?

    fun targets(
        checkpoint: CollaborationReadinessCheckpointData,
        limit: Int,
    ): List<CollaborationReadinessControlTargetsRecord>

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
