package dev.fajar.hris.collaboration.domain.entities

import java.time.Instant
import java.util.UUID

/** Public-key candidate only; caller admission and signed provider verification remain required. */
data class CollaborationSpaceRecipientDevice(
    val spaceId: UUID,
    val accountId: UUID,
    val deviceId: UUID,
    val serverName: String,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val fingerprint: String,
    val receiveUntil: Instant,
    val publicationConfirmed: Boolean,
) {
    override fun toString() = "CollaborationSpaceRecipientDevice(<metadata>)"
}
