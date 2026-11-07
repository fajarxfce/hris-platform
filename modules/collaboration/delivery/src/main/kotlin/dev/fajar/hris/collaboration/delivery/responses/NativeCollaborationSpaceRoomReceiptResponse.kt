package dev.fajar.hris.collaboration.delivery.responses

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomOperation
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomReceiptObservation
import dev.fajar.hris.core.http.toResponse
import java.util.UUID

class NativeCollaborationSpaceRoomReceiptResponse(
    val accountId: UUID,
    observation: CollaborationSpaceRoomReceiptObservation,
) {
    val original = OriginalCollaborationSpaceRoomOperationResponse(observation.original)
    val receipt = observation.receipt?.toResponse()
}

/**
 * Echoes the complete supplied descriptor; it is not an old native or provider authentication
 * proof.
 */
class OriginalCollaborationSpaceRoomOperationResponse(original: CollaborationSpaceRoomOperation) {
    val operationId = original.operationId
    val spaceId = original.spaceId
    val conversationId = original.conversationId
    val nativeSessionId = original.nativeSessionId
    val nativeSessionVersion = original.nativeSessionVersion
    val deviceId = original.deviceId
    val expectedDeviceVersion = original.expectedDeviceVersion
    val expectedRequestRevision = original.expectedAttempt?.revision
    val expectedRequestAccountId = original.expectedAttempt?.accountId
    val platform = original.client.platform
    val clientBuild = original.client.build
}
