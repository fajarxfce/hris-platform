package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.database.commandFingerprint
import dev.fajar.hris.schema.tables.records.CollaborationSpaceRoomRequestFencesRecord
import java.time.ZoneOffset

internal fun CollaborationSpaceRoomRequestFencesRecord.toRoomRequestFence():
    CollaborationSpaceRoomRequestFence {
    require((expectedRequestAccountId == null) == (expectedRequestRevision == null))
    val original =
        CollaborationSpaceRoomRequestIntent(
            operationId,
            spaceId,
            conversationId,
            deviceId,
            expectedDeviceVersion,
            expectedRequestRevision?.let {
                CollaborationSpaceRoomObservedAttempt(requireNotNull(expectedRequestAccountId), it)
            },
        )
    require(payloadHash == commandFingerprint(collaborationSpaceRoomReceiptKey(original).payload))
    return CollaborationSpaceRoomRequestFence(accountId, original, fenceId, createdAt.toInstant())
        .also { require(validCollaborationSpaceRoomRequestFence(it)) }
}

internal fun CollaborationSpaceRoomRequestFence.toRoomRequestFenceRecord():
    CollaborationSpaceRoomRequestFencesRecord {
    require(validCollaborationSpaceRoomRequestFence(this))
    val value = this
    return CollaborationSpaceRoomRequestFencesRecord().apply {
        accountId = value.accountId
        operationId = value.original.operationId
        spaceId = value.original.spaceId
        conversationId = value.original.conversationId
        deviceId = value.original.deviceId
        expectedDeviceVersion = value.original.expectedDeviceVersion
        expectedRequestAccountId = value.original.expectedAttempt?.accountId
        expectedRequestRevision = value.original.expectedAttempt?.revision
        payloadHash = commandFingerprint(collaborationSpaceRoomReceiptKey(value.original).payload)
        fenceId = value.id
        createdAt = value.createdAt.atOffset(ZoneOffset.UTC)
    }
}
