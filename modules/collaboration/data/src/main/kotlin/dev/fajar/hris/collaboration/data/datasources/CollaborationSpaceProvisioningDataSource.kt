package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceProvisioningRecord
import java.time.OffsetDateTime
import java.util.UUID

data class CollaborationSpaceProvisioningLeaseData(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
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
    override fun toString() = "CollaborationSpaceProvisioningLeaseData(redacted)"
}

interface CollaborationSpaceProvisioningDataSource {
    fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): CollaborationSpaceProvisioningRecord?

    fun insert(row: CollaborationSpaceProvisioningRecord)

    fun retry(row: CollaborationSpaceProvisioningRecord, expectedRevision: Long): Boolean

    fun claim(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int,
    ): List<CollaborationSpaceProvisioningRecord>

    fun finish(lease: CollaborationSpaceProvisioningLeaseData, failure: String?): Boolean

    fun defer(lease: CollaborationSpaceProvisioningLeaseData, delaySeconds: Int): Boolean

    fun cancel(spaceId: UUID, accountId: UUID, deviceId: UUID, expectedRevision: Long): Boolean
}
