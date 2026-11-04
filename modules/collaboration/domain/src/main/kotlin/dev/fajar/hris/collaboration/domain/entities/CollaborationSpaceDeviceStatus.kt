package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Current owned setup metadata, without provider credentials or internal claim/native proof. */
data class CollaborationSpaceDeviceStatus(
    val id: UUID,
    val version: Long,
    val state: CollaborationDeviceState,
    val platform: CollaborationPlatform,
    val origin: CollaborationSpaceDeviceOrigin,
    val label: String,
    val provisioning: CollaborationSpaceProvisioningStatus,
    val observedAt: Instant,
    val gateway: CollaborationSpaceGatewayStatus?,
)

/** Owned recovery metadata; no credential, provider authority or current access grant. */
data class CollaborationSpaceGatewayStatus(
    val id: UUID,
    val version: Long,
    val clientBuild: Int,
    val accessExpiresAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
)

data class CollaborationSpaceProvisioningStatus(
    val revision: Long,
    val state: CollaborationSpaceProvisioningState,
    val attempts: Int,
    val expiresAt: Instant,
    val lastOutcome: CollaborationSpaceProvisioningOutcome?,
    val retryNotBefore: Instant?,
)
