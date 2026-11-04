package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

data class CollaborationLegacyGatewayRetirement(val id: UUID, val version: Long)

/** Immutable transfer provenance. The caller still needs current native/group admission. */
data class CollaborationLegacyRuntimeCompletion(
    val owner: CollaborationLegacyRuntimeOwner,
    val target: CollaborationLegacyRuntimeTarget,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val fingerprint: String,
    val nativeSessionVersion: Long,
    val provisioningRevision: Long,
    val sourceDeviceVersion: Long,
    val sourceProviderGeneration: Long,
    val sourceGateway: CollaborationLegacyGatewayRetirement?,
    val targetDeviceVersion: Long,
    val targetProviderGeneration: Long,
    val frozenAt: Instant,
    val keysObservedAt: Instant,
    val completedAt: Instant,
)

/** Current admitted observations, including actual provider key readback, owned by the use case. */
data class CompleteCollaborationLegacyRuntimeRequest(
    val owner: CollaborationLegacyRuntimeOwner,
    val target: CollaborationLegacyRuntimeTarget,
    val nativeSessionVersion: Long,
    val targetDeviceVersion: Long,
    val targetProviderGeneration: Long,
    val provisioningRevision: Long,
    val legacyDeviceVersion: Long,
    val legacyProviderGeneration: Long,
    val legacyGatewayVersion: Long?,
    val fingerprint: String,
    val keysObservedAt: Instant,
)
