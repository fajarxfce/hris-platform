package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceDirectConversationsRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyConversationsRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceMembersRecord
import java.util.UUID

interface CollaborationSpaceConversationDataSource {
    fun member(spaceId: UUID, accountId: UUID): List<CollaborationSpaceMembersRecord>

    fun lockPair(spaceId: UUID, first: UUID, second: UUID)

    fun findPair(
        spaceId: UUID,
        first: UUID,
        second: UUID,
    ): CollaborationSpaceDirectConversationsRecord?

    fun find(spaceId: UUID, accountId: UUID, id: UUID): CollaborationSpaceDirectConversationsRecord?

    fun list(
        spaceId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): List<CollaborationSpaceDirectConversationsRecord>

    fun insert(record: CollaborationSpaceDirectConversationsRecord)

    fun legacy(
        spaceId: UUID,
        accountId: UUID,
        afterCompany: UUID?,
        afterConversation: UUID?,
        limit: Int,
    ): List<CollaborationSpaceLegacyConversationsRecord>

    fun legacyPair(
        spaceId: UUID,
        accountId: UUID,
        companyId: UUID,
        first: UUID,
        second: UUID,
    ): List<CollaborationSpaceLegacyConversationsRecord>

    fun legacyOne(
        spaceId: UUID,
        accountId: UUID,
        companyId: UUID,
        conversationId: UUID,
    ): CollaborationSpaceLegacyConversationsRecord?

    fun ownedLegacyRooms(
        spaceId: UUID,
        accountId: UUID,
        companyId: UUID,
        serverName: String,
        limit: Int,
    ): List<CollaborationSpaceLegacyConversationsRecord>

    fun legacyBoundRoom(
        spaceId: UUID,
        accountId: UUID,
        companyId: UUID,
        serverName: String,
        roomId: String,
    ): List<CollaborationSpaceLegacyConversationsRecord>
}
