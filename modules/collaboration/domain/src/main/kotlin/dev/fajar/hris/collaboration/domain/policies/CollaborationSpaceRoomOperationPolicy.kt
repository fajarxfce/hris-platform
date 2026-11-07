package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomOperation
import dev.fajar.hris.core.domain.OperationKey
import java.util.UUID

fun validCollaborationSpaceRoomOperation(value: CollaborationSpaceRoomOperation): Boolean =
    listOf(
            value.operationId,
            value.spaceId,
            value.conversationId,
            value.nativeSessionId,
            value.deviceId,
        )
        .none { it == UUID(0, 0) } &&
        value.nativeSessionVersion in 0..999999 &&
        value.expectedDeviceVersion in 0..999999 &&
        (value.expectedAttempt == null ||
            (value.expectedAttempt.accountId != UUID(0, 0) &&
                value.expectedAttempt.revision in 0..999999)) &&
        value.client.platform in setOf(ClientPlatform.ANDROID, ClientPlatform.IOS) &&
        value.client.build > 0

/** Persisted wire fingerprint: field order and the two "initial" sentinels are immutable. */
fun collaborationSpaceRoomOperationKey(value: CollaborationSpaceRoomOperation) =
    OperationKey(
        "collaboration.space_room_setup",
        value.operationId,
        listOf(
            value.spaceId.toString(),
            value.conversationId.toString(),
            value.nativeSessionId.toString(),
            value.nativeSessionVersion.toString(),
            value.deviceId.toString(),
            value.expectedDeviceVersion.toString(),
            value.expectedAttempt?.revision?.toString() ?: "initial",
            value.expectedAttempt?.accountId?.toString() ?: "initial",
            value.client.platform.name,
            value.client.build.toString(),
        ),
    )
