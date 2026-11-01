package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationPushHintsRecord
import java.time.OffsetDateTime
import java.util.UUID

data class CollaborationPushLeaseData(
    val companyId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val ownerId: UUID,
    val token: UUID,
    val revision: Long,
    val until: OffsetDateTime,
) {
    override fun toString() = "CollaborationPushLeaseData(<redacted>)"
}

interface CollaborationPushQueueDataSource {
    fun append(
        companyId: UUID,
        deviceId: UUID,
        accountId: UUID,
        parentId: UUID,
        roomId: String?,
        eventId: String?,
        eventHash: String?,
        lifetimeSeconds: Int,
    ): Boolean

    fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): CollaborationPushHintsRecord?

    fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): List<CollaborationPushHintsRecord>

    fun finish(lease: CollaborationPushLeaseData): Boolean

    fun defer(lease: CollaborationPushLeaseData, availableAt: OffsetDateTime): Boolean
}
