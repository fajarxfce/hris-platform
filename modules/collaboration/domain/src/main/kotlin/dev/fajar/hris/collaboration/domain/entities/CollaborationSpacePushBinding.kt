package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Retained push provenance; neither this record nor its key grants current room access. */
data class CollaborationSpacePushBinding(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val nativeSessionId: UUID,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val version: Long,
    val reservedAt: Instant,
    val publishedAt: Instant?,
) {
    override fun toString() = "CollaborationSpacePushBinding(<metadata>)"
}

class CollaborationSpacePushKey(val value: String) {
    override fun toString() = "CollaborationSpacePushKey(<redacted>)"
}
