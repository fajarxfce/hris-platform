package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationDirectConversationsRecord
import java.util.UUID

interface CollaborationDirectDataSource {
    fun lockPair(companyId: UUID, first: UUID, second: UUID)

    fun findPair(
        companyId: UUID,
        first: UUID,
        second: UUID,
    ): CollaborationDirectConversationsRecord?

    fun find(companyId: UUID, id: UUID): CollaborationDirectConversationsRecord?

    fun list(
        companyId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): List<CollaborationDirectConversationsRecord>

    fun insert(record: CollaborationDirectConversationsRecord)
}
