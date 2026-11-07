package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** A bounded provider observation; no bearer, SDK ownership, freeze or activation authority. */
data class CollaborationLegacyPublication(
    val spaceId: UUID,
    val deviceId: UUID,
    val deviceVersion: Long,
    val original: CollaborationLegacyDevicePosition,
    val fingerprint: String,
    val observedAt: Instant,
    val expiresAt: Instant,
) {
    override fun toString() = "CollaborationLegacyPublication(<metadata>)"
}
