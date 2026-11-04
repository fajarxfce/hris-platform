package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpacePushHintsRecord
import java.time.OffsetDateTime
import java.util.UUID

data class CollaborationSpacePushLeaseData(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val ownerId: UUID,
    val token: UUID,
    val revision: Long,
    val until: OffsetDateTime,
) {
    override fun toString() = "CollaborationSpacePushLeaseData(<redacted>)"
}

interface CollaborationSpacePushQueueDataSource {
    fun append(
        spaceId: UUID,
        deviceId: UUID,
        accountId: UUID,
        parentId: UUID,
        roomId: String?,
        eventId: String?,
        eventHash: String?,
        lifetimeSeconds: Int,
    ): Boolean

    fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): CollaborationSpacePushHintsRecord?

    fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): List<CollaborationSpacePushHintsRecord>

    fun finish(lease: CollaborationSpacePushLeaseData): Boolean

    fun defer(lease: CollaborationSpacePushLeaseData, availableAt: OffsetDateTime): Boolean
}
