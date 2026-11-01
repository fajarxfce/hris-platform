package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationDirectDataSource
import dev.fajar.hris.collaboration.data.datasources.CollaborationDirectSummaryRow
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationDirectRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationDirectConversationsRecord
import java.time.ZoneOffset
import java.util.UUID

class StoredCollaborationDirectRepository(private val store: CollaborationDirectDataSource) :
    CollaborationDirectRepository {
    override fun lockPair(companyId: UUID, first: UUID, second: UUID): Result<Unit> =
        safeDatabaseCall {
            store.lockPair(companyId, first, second)
        }

    override fun findPair(
        companyId: UUID,
        first: UUID,
        second: UUID,
    ): Result<CollaborationDirectConversation?> = safeDatabaseCall {
        store.findPair(companyId, first, second)?.domain()
    }

    override fun find(companyId: UUID, id: UUID): Result<CollaborationDirectConversation?> =
        safeDatabaseCall {
            store.find(companyId, id)?.domain()
        }

    override fun list(
        companyId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationDirectConversation>> = safeDatabaseCall {
        require(limit in 1..50)
        val rows = store.list(companyId, accountId, after, limit + 1)
        val items = rows.take(limit).map { it.domain() }
        Page(items, if (rows.size > limit) items.last().id.toString() else null)
    }

    override fun summaries(
        companyId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationDirectSummary>> = safeDatabaseCall {
        require(limit in 1..50)
        val rows = store.summaries(companyId, accountId, after, limit + 1)
        val items = rows.take(limit).map { it.summary(companyId, accountId) }
        Page(items, if (rows.size > limit) items.last().conversation.id.toString() else null)
    }

    override fun summary(
        companyId: UUID,
        accountId: UUID,
        id: UUID,
    ): Result<CollaborationDirectSummary?> = safeDatabaseCall {
        store.summary(companyId, accountId, id)?.summary(companyId, accountId)
    }

    private fun CollaborationDirectSummaryRow.summary(
        companyId: UUID,
        accountId: UUID,
    ): CollaborationDirectSummary {
        val value = conversation.domain()
        require(
            value.companyId == companyId &&
                accountId in setOf(value.firstAccountId, value.secondAccountId)
        )
        require(peerDisplayName.isNotBlank() && peerDisplayName.length <= 200)
        val peer =
            if (value.firstAccountId == accountId) value.secondAccountId else value.firstAccountId
        return CollaborationDirectSummary(
            value,
            CollaborationParticipantReference(peer, peerDisplayName),
        )
    }

    override fun create(conversation: CollaborationDirectConversation): Result<Unit> =
        safeDatabaseCall {
            store.insert(
                CollaborationDirectConversationsRecord().apply {
                    companyId = conversation.companyId
                    id = conversation.id
                    firstAccountId = conversation.firstAccountId
                    secondAccountId = conversation.secondAccountId
                    createdBy = conversation.createdBy
                    createdAt = conversation.createdAt.atOffset(ZoneOffset.UTC)
                    version = conversation.version
                }
            )
        }

    private fun CollaborationDirectConversationsRecord.domain() =
        CollaborationDirectConversation(
            id,
            companyId,
            firstAccountId,
            secondAccountId,
            createdBy,
            createdAt.toInstant(),
            version,
        )
}
