package dev.fajar.hris.collaboration.delivery.responses

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomReceiptObservation
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomRequestIntent
import dev.fajar.hris.core.http.toResponse
import java.util.UUID

class NativeCollaborationSpaceRoomIntentReceiptResponse(
    val accountId: UUID,
    observation: CollaborationSpaceRoomReceiptObservation<CollaborationSpaceRoomRequestIntent>,
) {
    val original = OriginalCollaborationSpaceRoomRequestIntentResponse(observation.original)
    val receipt = observation.receipt?.toResponse()
}

class OriginalCollaborationSpaceRoomRequestIntentResponse(
    original: CollaborationSpaceRoomRequestIntent
) {
    val operationId = original.operationId
    val spaceId = original.spaceId
    val conversationId = original.conversationId
    val deviceId = original.deviceId
    val expectedDeviceVersion = original.expectedDeviceVersion
    val expectedRequestRevision = original.expectedAttempt?.revision
    val expectedRequestAccountId = original.expectedAttempt?.accountId
}
