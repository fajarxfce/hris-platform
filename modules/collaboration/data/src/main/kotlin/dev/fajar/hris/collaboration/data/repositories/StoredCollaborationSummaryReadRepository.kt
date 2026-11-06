package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSummaryReadRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import java.util.UUID

class StoredCollaborationSummaryReadRepository(
    private val source: CollaborationSummaryReadDataSource
) : CollaborationSummaryReadRepository {
    override fun head(companyId: UUID) = safeDatabaseCall {
        val row = source.head(companyId)
        require(row.companyId == companyId)
        CollaborationSummaryHead(
            requireNotNull(row.epoch),
            requireNotNull(row.headPosition),
            requireNotNull(row.prunedThrough),
        )
    }

    override fun snapshot(owner: CollaborationSummaryOwner, after: UUID?, limit: Int) =
        safeDatabaseCall {
            require(limit in 1..51)
            source.snapshot(owner.companyId, owner.accountId, after, limit).map { fact(owner, it) }
        }

    override fun changes(owner: CollaborationSummaryOwner, after: Long, upper: Long, limit: Int) =
        safeDatabaseCall {
            require(after >= 0 && upper >= after && limit in 1..51)
            source.changes(owner.companyId, owner.accountId, after, upper, limit).map {
                CollaborationSummaryReference(
                    requireNotNull(it.sequence),
                    requireNotNull(it.resourceId),
                    requireNotNull(it.resourceVersion),
                )
            }
        }

    override fun facts(owner: CollaborationSummaryOwner, ids: List<UUID>) = safeDatabaseCall {
        require(ids.size in 1..50 && ids.distinct().size == ids.size)
        source.facts(owner.companyId, owner.accountId, ids).map { fact(owner, it) }
    }

    override fun pending(owner: CollaborationSummaryOwner) = safeDatabaseCall {
        source.pending(owner.companyId, owner.accountId)
    }

    private fun fact(
        owner: CollaborationSummaryOwner,
        row: CollaborationSummaryFactRow,
    ): CollaborationSummaryFact {
        val record = row.record
        val direct = row.conversation
        require(
            record.companyId == owner.companyId &&
                record.ownerAccountId == owner.accountId &&
                direct.companyId == owner.companyId &&
                direct.id == record.conversationId
        )
        require(
            direct.firstAccountId != direct.secondAccountId &&
                owner.accountId in setOf(direct.firstAccountId, direct.secondAccountId)
        )
        val peer =
            if (direct.firstAccountId == owner.accountId) direct.secondAccountId
            else direct.firstAccountId
        require(
            record.peerAccountId == peer &&
                requireNotNull(record.version) >= 0 &&
                direct.version == 0L
        )
        val summary =
            record.peerDisplayName?.let { name ->
                require(name.isNotBlank() && name.length <= 200)
                CollaborationDirectSummary(
                    CollaborationDirectConversation(
                        requireNotNull(direct.id),
                        requireNotNull(direct.companyId),
                        requireNotNull(direct.firstAccountId),
                        requireNotNull(direct.secondAccountId),
                        requireNotNull(direct.createdBy),
                        requireNotNull(direct.createdAt).toInstant(),
                        requireNotNull(direct.version),
                    ),
                    CollaborationParticipantReference(requireNotNull(peer), name),
                )
            }
        return CollaborationSummaryFact(
            requireNotNull(record.conversationId),
            requireNotNull(record.version),
            summary,
        )
    }
}
