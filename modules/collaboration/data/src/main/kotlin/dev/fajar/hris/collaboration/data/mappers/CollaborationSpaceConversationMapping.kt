package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpaceServer
import dev.fajar.hris.schema.tables.records.*
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID

fun requireSpaceConversationIds(vararg ids: UUID) {
    require(ids.none { it == UUID(0, 0) })
}

fun requireSpaceConversationPair(space: UUID, first: UUID, second: UUID) {
    requireSpaceConversationIds(space, first, second)
    require(first.toString() < second.toString())
}

private fun requireConversationTime(at: Instant) {
    require(at.nano % 1000 == 0 && at.epochSecond in -62135596800L..253402300799L)
}

fun CollaborationSpaceMembersRecord.toSpaceMember(
    space: UUID,
    account: UUID,
): CollaborationSpaceMember {
    requireSpaceConversationIds(space, account)
    require(spaceId == space && accountId == account)
    val name = requireNotNull(displayName)
    require(name.isNotBlank() && name.length <= 200)
    val identity =
        if (matrixServerName == null && matrixUserId == null && identitySelectedAt == null) null
        else {
            val server = requireNotNull(matrixServerName)
            val user = requireNotNull(matrixUserId)
            val at = requireNotNull(identitySelectedAt).toInstant()
            require(validCollaborationSpaceServer(server))
            val local = user.substringBefore(':')
            val accountName = account.toString().replace("-", "")
            require(user.length <= 255 && user.substringAfter(':', "") == server)
            require(
                local == "@hrisg_${space.toString().replace("-", "")}_$accountName" ||
                    local.matches(Regex("@hris_[a-f0-9]{32}_$accountName"))
            )
            requireConversationTime(at)
            CollaborationSpacePublicIdentity(server, user, at)
        }
    return CollaborationSpaceMember(space, account, name, identity)
}

fun CollaborationSpaceDirectConversation.toSpaceConversationRecord():
    CollaborationSpaceDirectConversationsRecord {
    requireSpaceConversationIds(id, createdBy)
    requireSpaceConversationPair(spaceId, firstAccountId, secondAccountId)
    require(createdBy == firstAccountId || createdBy == secondAccountId)
    require(version == 0L)
    requireConversationTime(createdAt)
    return CollaborationSpaceDirectConversationsRecord().also {
        it.spaceId = spaceId
        it.id = id
        it.firstAccountId = firstAccountId
        it.secondAccountId = secondAccountId
        it.createdBy = createdBy
        it.createdAt = createdAt.atOffset(UTC)
        it.version = version
    }
}

fun CollaborationSpaceDirectConversationsRecord.toSpaceConversation(
    space: UUID,
    account: UUID? = null,
): CollaborationSpaceDirectConversation {
    require(spaceId == space)
    val value =
        CollaborationSpaceDirectConversation(
            requireNotNull(id),
            space,
            requireNotNull(firstAccountId),
            requireNotNull(secondAccountId),
            requireNotNull(createdBy),
            requireNotNull(createdAt).toInstant(),
            requireNotNull(version),
        )
    value.toSpaceConversationRecord()
    require(account == null || account == value.firstAccountId || account == value.secondAccountId)
    return value
}

fun CollaborationSpaceLegacyConversationsRecord.toSpaceLegacyConversation(
    space: UUID,
    account: UUID,
): CollaborationSpaceLegacyConversation {
    requireSpaceConversationIds(space, account)
    require(spaceId == space)
    val company = requireNotNull(legacyCompanyId)
    val id = requireNotNull(legacyConversationId)
    val first = requireNotNull(firstAccountId)
    val second = requireNotNull(secondAccountId)
    val creator = requireNotNull(createdBy)
    val at = requireNotNull(createdAt).toInstant()
    val revision = requireNotNull(version)
    requireSpaceConversationIds(id, creator)
    requireSpaceConversationPair(company, first, second)
    require(creator == first || creator == second)
    require(account == first || account == second)
    require(revision == 0L)
    requireConversationTime(at)
    val room =
        if (
            matrixServerName == null &&
                matrixRoomId == null &&
                roomBoundAt == null &&
                roomCreatedBy == null
        )
            null
        else {
            val server = requireNotNull(matrixServerName)
            val roomId = requireNotNull(matrixRoomId)
            val bound = requireNotNull(roomBoundAt).toInstant()
            val roomCreator = requireNotNull(roomCreatedBy)
            require(roomCreator == first || roomCreator == second)
            require(validCollaborationSpaceServer(server))
            require(
                roomId.length in 2..255 &&
                    roomId.startsWith('!') &&
                    roomId.none { it.isWhitespace() || it.isISOControl() }
            )
            requireConversationTime(bound)
            require(bound >= at)
            CollaborationLegacyRoomMetadata(server, roomId, bound, roomCreator)
        }
    return CollaborationSpaceLegacyConversation(
        space,
        CollaborationDirectConversation(id, company, first, second, creator, at, revision),
        room,
    )
}

fun CollaborationSpaceLegacyConversation.position() =
    CollaborationLegacyConversationPosition(conversation.companyId, conversation.id)

fun CollaborationLegacyConversationPosition.orderKey(): String = "$companyId/$conversationId"
