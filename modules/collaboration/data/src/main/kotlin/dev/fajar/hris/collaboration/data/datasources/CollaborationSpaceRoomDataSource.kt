package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID

data class CollaborationSpaceRoomLeaseData(
    val spaceId: UUID,
    val conversationId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val deviceVersion: Long,
    val providerGeneration: Long,
    val nativeSessionId: UUID,
    val revision: Long,
    val nativeSessionVersion: Long,
    val credentialVersion: Long,
    val authenticatedAt: OffsetDateTime,
    val mfaVerifiedAt: OffsetDateTime?,
    val correlationId: UUID,
    val clientPlatform: String,
    val clientBuild: Long,
    val requestedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
    val attempt: Int,
    val ownerId: UUID,
    val token: UUID,
    val until: OffsetDateTime,
) {
    override fun toString() = "CollaborationSpaceRoomLeaseData(redacted)"
}

data class CollaborationSpaceRoomBindingData(
    val intent: CollaborationSpaceRoomIntentsRecord,
    val binding: CollaborationSpaceRoomBindingsRecord,
)

interface CollaborationSpaceRoomDataSource {
    fun lock(spaceId: UUID, conversationId: UUID)

    fun intent(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): CollaborationSpaceRoomIntentsRecord?

    fun insertIntent(row: CollaborationSpaceRoomIntentsRecord)

    fun binding(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): CollaborationSpaceRoomBindingData?

    fun insertBinding(row: CollaborationSpaceRoomBindingsRecord)

    fun status(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
    ): List<CollaborationSpaceRoomStatusRecord>

    fun find(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
        lock: Boolean,
    ): CollaborationSpaceRoomProvisioningRecord?

    fun insert(row: CollaborationSpaceRoomProvisioningRecord)

    fun retry(row: CollaborationSpaceRoomProvisioningRecord, expectedRevision: Long): Boolean

    fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
    ): List<CollaborationSpaceRoomProvisioningRecord>

    fun finish(lease: CollaborationSpaceRoomLeaseData, failure: String?): Boolean

    fun defer(lease: CollaborationSpaceRoomLeaseData, delaySeconds: Int): Boolean

    fun cancel(
        spaceId: UUID,
        accountId: UUID,
        conversationId: UUID,
        expectedRevision: Long,
    ): Boolean
}
