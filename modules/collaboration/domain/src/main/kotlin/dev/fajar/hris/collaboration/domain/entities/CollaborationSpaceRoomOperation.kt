package dev.fajar.hris.collaboration.domain.entities

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.core.domain.MutationReceipt
import java.util.UUID

data class CollaborationSpaceRoomObservedAttempt(val accountId: UUID, val revision: Long)

/** Complete original idempotency descriptor. It contains no native/provider credential. */
data class CollaborationSpaceRoomOperation(
    val operationId: UUID,
    val spaceId: UUID,
    val conversationId: UUID,
    val nativeSessionId: UUID,
    val nativeSessionVersion: Long,
    val deviceId: UUID,
    val expectedDeviceVersion: Long,
    val expectedAttempt: CollaborationSpaceRoomObservedAttempt?,
    val client: ClientVersion,
) {
    override fun toString() = "CollaborationSpaceRoomOperation([metadata])"
}

/** Receipt absence remains uncertain; it cannot fence a delayed original or authorize a retry. */
data class CollaborationSpaceRoomReceiptObservation(
    val original: CollaborationSpaceRoomOperation,
    val receipt: MutationReceipt?,
)
