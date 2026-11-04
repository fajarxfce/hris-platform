package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceConversationDataSource
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceConversationRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import java.util.UUID

class StoredCollaborationSpaceConversationRepository(
    private val source: CollaborationSpaceConversationDataSource
) : CollaborationSpaceConversationRepository {
    override fun member(spaceId: UUID, accountId: UUID): Result<CollaborationSpaceMember?> =
        safeDatabaseCall {
            requireSpaceConversationIds(spaceId, accountId)
            val rows = source.member(spaceId, accountId)
            require(rows.size <= 1)
            rows.singleOrNull()?.toSpaceMember(spaceId, accountId)
        }

    override fun lockPair(spaceId: UUID, first: UUID, second: UUID): Result<Unit> =
        safeDatabaseCall {
            requireSpaceConversationPair(spaceId, first, second)
            source.lockPair(spaceId, first, second)
        }

    override fun findPair(
        spaceId: UUID,
        first: UUID,
        second: UUID,
    ): Result<CollaborationSpaceDirectConversation?> = safeDatabaseCall {
        requireSpaceConversationPair(spaceId, first, second)
        source.findPair(spaceId, first, second)?.toSpaceConversation(spaceId)?.also {
            require(it.firstAccountId == first && it.secondAccountId == second)
        }
    }

    override fun find(
        spaceId: UUID,
        accountId: UUID,
        id: UUID,
    ): Result<CollaborationSpaceDirectConversation?> = safeDatabaseCall {
        requireSpaceConversationIds(spaceId, accountId, id)
        source.find(spaceId, accountId, id)?.toSpaceConversation(spaceId, accountId)?.also {
            require(it.id == id)
        }
    }

    override fun list(
        spaceId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationSpaceDirectConversation>> = safeDatabaseCall {
        requireSpaceConversationIds(spaceId, accountId, *listOfNotNull(after).toTypedArray())
        require(limit in 1..50)
        val rows = source.list(spaceId, accountId, after, limit + 1)
        require(rows.size <= limit + 1)
        val values = rows.map { it.toSpaceConversation(spaceId, accountId) }
        requireForwardPage(values.map { it.id.toString() }, after?.toString())
        val items = values.take(limit)
        Page(items, if (values.size > limit) items.last().id.toString() else null)
    }

    override fun create(value: CollaborationSpaceDirectConversation): Result<Unit> =
        safeDatabaseCall {
            source.insert(value.toSpaceConversationRecord())
            require(
                source
                    .find(value.spaceId, value.createdBy, value.id)
                    ?.toSpaceConversation(value.spaceId, value.createdBy) == value
            )
        }

    override fun legacy(
        spaceId: UUID,
        accountId: UUID,
        after: CollaborationLegacyConversationPosition?,
        limit: Int,
    ): Result<CollaborationSpaceLegacyConversationPage> = safeDatabaseCall {
        requireSpaceConversationIds(spaceId, accountId)
        after?.let { requireSpaceConversationIds(it.companyId, it.conversationId) }
        require(limit in 1..50)
        val rows =
            source.legacy(spaceId, accountId, after?.companyId, after?.conversationId, limit + 1)
        require(rows.size <= limit + 1)
        val values = rows.map { it.toSpaceLegacyConversation(spaceId, accountId) }
        requireForwardPage(values.map { it.position().orderKey() }, after?.orderKey())
        val items = values.take(limit)
        CollaborationSpaceLegacyConversationPage(
            items,
            if (values.size > limit) items.last().position() else null,
        )
    }

    override fun legacyPair(
        spaceId: UUID,
        accountId: UUID,
        companyId: UUID,
        first: UUID,
        second: UUID,
    ): Result<CollaborationSpaceLegacyConversation?> = safeDatabaseCall {
        requireSpaceConversationIds(spaceId, accountId)
        requireSpaceConversationPair(companyId, first, second)
        require(accountId == first || accountId == second)
        val rows = source.legacyPair(spaceId, accountId, companyId, first, second)
        require(rows.size <= 1)
        rows.singleOrNull()?.toSpaceLegacyConversation(spaceId, accountId)?.also {
            require(
                it.conversation.companyId == companyId &&
                    it.conversation.firstAccountId == first &&
                    it.conversation.secondAccountId == second
            )
        }
    }

    override fun legacyOne(
        spaceId: UUID,
        accountId: UUID,
        position: CollaborationLegacyConversationPosition,
    ): Result<CollaborationSpaceLegacyConversation?> = safeDatabaseCall {
        requireSpaceConversationIds(spaceId, accountId, position.companyId, position.conversationId)
        source
            .legacyOne(spaceId, accountId, position.companyId, position.conversationId)
            ?.toSpaceLegacyConversation(spaceId, accountId)
            ?.also { require(it.position() == position) }
    }

    private fun requireForwardPage(keys: List<String>, after: String?) {
        require(keys.zipWithNext().all { (a, b) -> a < b })
        require(after == null || keys.all { it > after })
    }

    override fun ownedLegacyRooms(
        spaceId: UUID,
        accountId: UUID,
        companyId: UUID,
        serverName: String,
        limit: Int,
    ): Result<List<CollaborationSpaceLegacyConversation>> = safeDatabaseCall {
        requireSpaceConversationIds(spaceId, accountId, companyId)
        require(validCollaborationSpaceServer(serverName))
        require(limit in 1..COLLABORATION_SYNC_ROOM_LIMIT + 1)
        val rows = source.ownedLegacyRooms(spaceId, accountId, companyId, serverName, limit)
        require(rows.size <= limit)
        val values =
            rows.map {
                it.toSpaceLegacyConversation(spaceId, accountId).also { value ->
                    requireOriginalRoom(value, companyId, serverName)
                }
            }
        requireForwardPage(values.map { it.conversation.id.toString() }, null)
        require(values.map { requireNotNull(it.room).roomId }.toSet().size == values.size)
        values
    }

    override fun legacyBoundRoom(
        spaceId: UUID,
        accountId: UUID,
        companyId: UUID,
        serverName: String,
        roomId: String,
    ): Result<CollaborationSpaceLegacyConversation?> = safeDatabaseCall {
        requireSpaceConversationIds(spaceId, accountId, companyId)
        require(validCollaborationSpaceServer(serverName) && validCollaborationSpaceRoomId(roomId))
        val rows = source.legacyBoundRoom(spaceId, accountId, companyId, serverName, roomId)
        require(rows.size <= 1)
        rows.singleOrNull()?.toSpaceLegacyConversation(spaceId, accountId)?.also {
            requireOriginalRoom(it, companyId, serverName)
            require(it.room?.roomId == roomId)
        }
    }

    private fun requireOriginalRoom(
        value: CollaborationSpaceLegacyConversation,
        companyId: UUID,
        serverName: String,
    ) {
        require(value.conversation.companyId == companyId)
        val room = requireNotNull(value.room)
        require(room.serverName == serverName && validCollaborationSpaceRoomId(room.roomId))
    }
}
