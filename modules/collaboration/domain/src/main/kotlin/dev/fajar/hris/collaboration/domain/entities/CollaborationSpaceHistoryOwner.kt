package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Original device metadata supplied by current group admission, never client authority. */
data class CollaborationSpaceHistoryOwner(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val serverName: String,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val legacy: CollaborationLegacyDevicePosition? = null,
) {
    override fun toString() = "CollaborationSpaceHistoryOwner(<redacted>)"
}
