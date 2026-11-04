package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceRoomBindingData
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset.UTC
import java.util.UUID

fun spaceRoomIntentRow(value: CollaborationSpaceRoomIntent): CollaborationSpaceRoomIntentsRecord {
    requireSpaceRoomIds(
        value.spaceId,
        value.conversationId,
        value.firstAccountId,
        value.secondAccountId,
        value.requestedBy,
        value.matrixCreatorAccountId,
    )
    require(value.firstAccountId.toString() < value.secondAccountId.toString())
    val pair = setOf(value.firstAccountId, value.secondAccountId)
    require(value.requestedBy in pair && value.matrixCreatorAccountId in pair)
    requireSpaceRoomTime(value.createdAt)
    require(
        validCollaborationSpaceRoomRequest(
            CollaborationSpaceMatrixDirectRoom(
                value.spaceId,
                value.conversationId,
                value.firstAccountId,
                value.secondAccountId,
                value.firstMatrixUserId,
                value.secondMatrixUserId,
            ),
            value.serverName,
        )
    )
    val original = value.original
    if (value.origin == CollaborationSpaceRoomOrigin.SPACE) {
        require(original == null && value.matrixCreatorAccountId == value.requestedBy)
    } else {
        requireNotNull(original)
        requireSpaceRoomIds(original.companyId, original.conversationId)
        requireSpaceRoomTime(original.boundAt)
        require(
            original.boundAt <= value.createdAt && validCollaborationSpaceRoomId(original.roomId)
        )
        require(
            value.firstMatrixUserId ==
                "@${collaborationMatrixLocalpart(original.companyId, value.firstAccountId)}:${value.serverName}"
        )
        require(
            value.secondMatrixUserId ==
                "@${collaborationMatrixLocalpart(original.companyId, value.secondAccountId)}:${value.serverName}"
        )
    }
    return CollaborationSpaceRoomIntentsRecord().also {
        it.spaceId = value.spaceId
        it.conversationId = value.conversationId
        it.firstAccountId = value.firstAccountId
        it.secondAccountId = value.secondAccountId
        it.requestedBy = value.requestedBy
        it.matrixCreatorAccountId = value.matrixCreatorAccountId
        it.matrixServerName = value.serverName
        it.firstMatrixUserId = value.firstMatrixUserId
        it.secondMatrixUserId = value.secondMatrixUserId
        it.origin = value.origin.name
        it.legacyCompanyId = original?.companyId
        it.legacyConversationId = original?.conversationId
        it.legacyRoomId = original?.roomId
        it.legacyBoundAt = original?.boundAt?.atOffset(UTC)
        it.createdAt = value.createdAt.atOffset(UTC)
    }
}

fun CollaborationSpaceRoomIntentsRecord.toSpaceRoomIntent(
    space: UUID,
    account: UUID,
    conversation: UUID,
): CollaborationSpaceRoomIntent {
    requireSpaceRoomIds(space, account, conversation)
    require(spaceId == space && conversationId == conversation)
    val original =
        if (
            legacyCompanyId == null &&
                legacyConversationId == null &&
                legacyRoomId == null &&
                legacyBoundAt == null
        )
            null
        else
            CollaborationSpaceOriginalRoom(
                requireNotNull(legacyCompanyId),
                requireNotNull(legacyConversationId),
                requireNotNull(legacyRoomId),
                requireNotNull(legacyBoundAt).toInstant(),
            )
    val value =
        CollaborationSpaceRoomIntent(
            space,
            conversation,
            requireNotNull(firstAccountId),
            requireNotNull(secondAccountId),
            requireNotNull(requestedBy),
            requireNotNull(matrixCreatorAccountId),
            requireNotNull(matrixServerName),
            requireNotNull(firstMatrixUserId),
            requireNotNull(secondMatrixUserId),
            CollaborationSpaceRoomOrigin.valueOf(requireNotNull(origin)),
            original,
            requireNotNull(createdAt).toInstant(),
        )
    require(account in setOf(value.firstAccountId, value.secondAccountId))
    spaceRoomIntentRow(value)
    return value
}

fun spaceRoomBindingRow(
    value: CollaborationSpaceRoomBinding
): CollaborationSpaceRoomBindingsRecord {
    requireSpaceRoomIds(value.spaceId, value.conversationId, value.publishedBy, value.leaseToken)
    require(value.requestRevision in 0..999999 && validCollaborationSpaceRoomId(value.roomId))
    requireSpaceRoomTime(value.createdAt)
    return CollaborationSpaceRoomBindingsRecord().also {
        it.spaceId = value.spaceId
        it.conversationId = value.conversationId
        it.publishedBy = value.publishedBy
        it.requestRevision = value.requestRevision
        it.leaseToken = value.leaseToken
        it.matrixRoomId = value.roomId
        it.createdAt = value.createdAt.atOffset(UTC)
    }
}

fun CollaborationSpaceRoomBindingData.toSpaceRoomBinding(
    space: UUID,
    account: UUID,
    conversation: UUID,
): CollaborationSpaceRoomBinding {
    val owner = intent.toSpaceRoomIntent(space, account, conversation)
    val row = binding
    require(row.spaceId == space && row.conversationId == conversation)
    val value =
        CollaborationSpaceRoomBinding(
            space,
            conversation,
            requireNotNull(row.publishedBy),
            requireNotNull(row.requestRevision),
            requireNotNull(row.leaseToken),
            requireNotNull(row.matrixRoomId),
            requireNotNull(row.createdAt).toInstant(),
        )
    spaceRoomBindingRow(value)
    require(value.publishedBy == owner.requestedBy && value.createdAt >= owner.createdAt)
    val original = owner.original
    require(original == null || original.roomId == value.roomId)
    return value
}
