package dev.fajar.hris.collaboration.data.datasources

import java.util.UUID

data class CollaborationOwnedRoomData(
    val companyId: UUID,
    val conversationId: UUID,
    val firstAccountId: UUID,
    val secondAccountId: UUID,
    val creatorAccountId: UUID,
    val serverName: String,
    val roomId: String,
)

interface CollaborationRoomScopeDataSource {
    fun owned(companyId: UUID, accountId: UUID, limit: Int): List<CollaborationOwnedRoomData>
}
