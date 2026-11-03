package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Retained crypto identity metadata, never permission to resume a provider session. */
data class CollaborationLegacyDevice(
    val position: CollaborationLegacyDevicePosition,
    val accountId: UUID,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val platform: CollaborationPlatform,
    val state: CollaborationDeviceState,
    val deviceVersion: Long,
    val credentialGeneration: Long,
    val deviceCreatedAt: Instant,
    val identityObservedAt: Instant,
    val deviceRevokedAt: Instant?,
    val credentialRetiredAt: Instant?,
) {
    override fun toString() = "CollaborationLegacyDevice(<metadata>)"
}

data class CollaborationLegacyDevicePosition(val companyId: UUID, val deviceId: UUID)

data class CollaborationLegacyDevicePage(
    val items: List<CollaborationLegacyDevice>,
    val next: CollaborationLegacyDevicePosition?,
)
