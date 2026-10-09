package dev.fajar.hris.communications.data.repositories

import dev.fajar.hris.communications.data.datasources.InboxDataSource
import dev.fajar.hris.communications.data.mappers.*
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.InboxRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

class StoredInboxRepository(private val source: InboxDataSource) : InboxRepository {
    override fun find(
        companyId: UUID,
        accountId: UUID,
        id: UUID,
        lock: Boolean,
    ): Result<InboxItem?> = safeDatabaseCall {
        source.find(companyId, accountId, id, lock)?.toItem()
    }

    override fun list(
        companyId: UUID,
        accountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<InboxSummary>> = safeDatabaseCall {
        val rows = source.list(companyId, accountId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toSummary() },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun saveReadState(
        companyId: UUID,
        accountId: UUID,
        id: UUID,
        expectedVersion: Long,
        readAt: Instant?,
        acknowledgedAt: Instant?,
    ): Result<MutationReceipt?> = safeDatabaseCall {
        if (
            source.updateReadState(
                companyId,
                accountId,
                id,
                expectedVersion,
                readAt,
                acknowledgedAt,
            )
        )
            MutationReceipt(id, expectedVersion + 1)
        else null
    }

    override fun withdraw(companyId: UUID, announcementId: UUID): Result<Int> = safeDatabaseCall {
        source.withdraw(companyId, announcementId)
    }
}
