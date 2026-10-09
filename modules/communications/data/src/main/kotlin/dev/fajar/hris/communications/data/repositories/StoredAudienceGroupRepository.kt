package dev.fajar.hris.communications.data.repositories

import dev.fajar.hris.communications.data.datasources.AudienceGroupDataSource
import dev.fajar.hris.communications.data.mappers.*
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.AudienceGroupRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredAudienceGroupRepository(
    private val source: AudienceGroupDataSource,
    private val json: ObjectMapper,
) : AudienceGroupRepository {
    override fun lock(companyId: UUID, shared: Boolean): Result<Unit> = safeDatabaseCall {
        source.lock(companyId, shared)
    }

    override fun count(companyId: UUID): Result<Int> = safeDatabaseCall { source.count(companyId) }

    override fun find(companyId: UUID, id: UUID, revision: Long?): Result<AudienceGroup?> =
        safeDatabaseCall {
            source.find(companyId, id, revision)?.toGroup(json)
        }

    override fun list(
        companyId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<AudienceGroupSummary>> = safeDatabaseCall {
        val rows = source.list(companyId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toSummary() },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun references(companyId: UUID, ids: Set<UUID>): Result<List<AudienceGroupSummary>> =
        safeDatabaseCall {
            require(ids.size <= 32)
            source.references(companyId, ids).map { it.toSummary() }
        }

    override fun save(
        companyId: UUID,
        snapshot: AudienceGroup,
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
