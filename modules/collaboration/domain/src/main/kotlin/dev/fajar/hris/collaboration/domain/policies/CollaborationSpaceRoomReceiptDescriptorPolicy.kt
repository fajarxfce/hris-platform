package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.OperationKey
import java.util.UUID

fun validCollaborationSpaceRoomReceiptDescriptor(
    value: CollaborationSpaceRoomReceiptDescriptor
): Boolean =
    when (value) {
        is CollaborationSpaceRoomOperation -> validCollaborationSpaceRoomOperation(value)
        is CollaborationSpaceRoomRequestIntent ->
            listOf(value.operationId, value.spaceId, value.conversationId, value.deviceId).none {
                it == UUID(0, 0)
            } &&
                value.expectedDeviceVersion in 0..999999 &&
                (value.expectedAttempt == null ||
                    (value.expectedAttempt.accountId != UUID(0, 0) &&
                        value.expectedAttempt.revision in 0..999999))
    }

/**
 * Legacy receipts retain their exact namespace and hash; the stable intent has a distinct version.
 */
fun collaborationSpaceRoomReceiptKey(value: CollaborationSpaceRoomReceiptDescriptor): OperationKey =
    when (value) {
        is CollaborationSpaceRoomOperation -> collaborationSpaceRoomOperationKey(value)
        is CollaborationSpaceRoomRequestIntent ->
            OperationKey(
                "collaboration.space_room_setup.v2",
                value.operationId,
                listOf(
                    value.spaceId.toString(),
                    value.conversationId.toString(),
                    value.deviceId.toString(),
                    value.expectedDeviceVersion.toString(),
                    value.expectedAttempt?.revision?.toString() ?: "initial",
                    value.expectedAttempt?.accountId?.toString() ?: "initial",
                ),
            )
    }

fun collaborationSpaceRoomRequestKey(
    format: CollaborationSpaceRoomRequestFormat,
    original: CollaborationSpaceRoomOperation,
): OperationKey =
    when (format) {
        CollaborationSpaceRoomRequestFormat.NATIVE_PROOF ->
            collaborationSpaceRoomOperationKey(original)
        CollaborationSpaceRoomRequestFormat.STABLE_INTENT ->
            collaborationSpaceRoomReceiptKey(
                CollaborationSpaceRoomRequestIntent(
                    original.operationId,
                    original.spaceId,
                    original.conversationId,
                    original.deviceId,
                    original.expectedDeviceVersion,
                    original.expectedAttempt,
                )
            )
    }
