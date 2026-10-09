package dev.fajar.hris.communications.data.repositories

import dev.fajar.hris.communications.data.datasources.AnnouncementDataSource
import dev.fajar.hris.communications.data.mappers.*
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.AnnouncementRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredAnnouncementRepository(
    private val source: AnnouncementDataSource,
    private val json: ObjectMapper,
) : AnnouncementRepository {
    override fun lock(companyId: UUID, shared: Boolean): Result<Unit> = safeDatabaseCall {
        source.lock(companyId, shared)
    }

    override fun count(companyId: UUID): Result<Int> = safeDatabaseCall { source.count(companyId) }

    override fun find(companyId: UUID, id: UUID, revision: Long?): Result<Announcement?> =
        safeDatabaseCall {
            source.find(companyId, id, revision)?.toAnnouncement(json)
        }

    override fun list(
        companyId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<AnnouncementSummary>> = safeDatabaseCall {
        val rows = source.list(companyId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toSummary() },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<AnnouncementSummary>> = safeDatabaseCall {
        val rows = source.history(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toSummary() },
            if (rows.size > limit) rows[limit - 1].version.toString() else null,
        )
    }

    override fun save(
        companyId: UUID,
        snapshot: Announcement,
        expectedVersion: Long?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) source.createHead(companyId, snapshot.id)
                else if (!source.advanceHead(companyId, snapshot.id, expectedVersion))
                    return@safeDatabaseCall null
                source.insertRevision(snapshot.toRecord(companyId, json))
                MutationReceipt(snapshot.id, snapshot.version)
            }
            .requireCurrentVersion()
}
