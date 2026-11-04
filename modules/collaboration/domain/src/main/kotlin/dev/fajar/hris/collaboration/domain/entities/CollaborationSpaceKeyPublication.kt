package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Immutable public identity, including a reservation whose provider outcome is still uncertain. */
data class CollaborationSpaceKeyPublication(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val fingerprint: String,
    val version: Long,
    val reservedAt: Instant,
    val publishedAt: Instant? = null,
) {
    override fun toString() = "CollaborationSpaceKeyPublication(<metadata>)"
}
