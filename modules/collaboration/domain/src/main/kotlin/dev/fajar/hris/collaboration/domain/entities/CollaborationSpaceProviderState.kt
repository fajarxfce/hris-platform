package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Durable credential provenance. A retired generation is distinct from an absent row. */
data class CollaborationSpaceProviderState(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val generation: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
    val clearedAt: Instant? = null,
) {
    override fun toString(): String = "CollaborationSpaceProviderState(redacted)"
}

data class CollaborationSpaceProviderSession(
    val state: CollaborationSpaceProviderState,
    val accessToken: String,
) {
    override fun toString(): String = "CollaborationSpaceProviderSession(redacted)"
}
