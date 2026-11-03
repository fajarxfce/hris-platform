package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Durable identity/provenance only; a current binding and provider admission remain mandatory. */
data class CollaborationSpaceDevice(
    val id: UUID,
    val spaceId: UUID,
    val accountId: UUID,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val origin: CollaborationSpaceDeviceOrigin,
    val legacy: CollaborationLegacyDevicePosition?,
    val legacyKeyFingerprint: String?,
    val originNativeSessionId: UUID,
    val platform: CollaborationPlatform,
    val label: String,
    val state: CollaborationDeviceState,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revokedAt: Instant?,
) {
    override fun toString() = "CollaborationSpaceDevice(<metadata>)"
}

enum class CollaborationSpaceDeviceOrigin {
    LEGACY,
    SPACE,
}
