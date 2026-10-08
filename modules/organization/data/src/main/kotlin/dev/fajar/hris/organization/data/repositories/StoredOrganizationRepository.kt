package dev.fajar.hris.organization.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.data.datasources.OrganizationDataSource
import dev.fajar.hris.organization.data.mappers.*
import dev.fajar.hris.organization.domain.entities.*
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import java.util.UUID

class StoredOrganizationRepository(private val source: OrganizationDataSource) :
    OrganizationRepository {
    override fun lockStructure(companyId: UUID): Result<Unit> = safeDatabaseCall {
        source.lock(companyId)
    }

    override fun find(companyId: UUID, id: UUID): Result<OrganizationUnit?> = safeDatabaseCall {
        source.find(companyId, id)?.toUnit()
    }

    override fun ancestors(companyId: UUID, parentId: UUID): Result<List<OrganizationUnit>> =
        safeDatabaseCall {
            source.ancestors(companyId, parentId).map { it.toUnit() }
        }

    override fun list(
        companyId: UUID,
        kind: UnitKind?,
        after: String?,
        limit: Int,
    ): Result<Page<OrganizationUnit>> = safeDatabaseCall {
        val rows = source.list(companyId, kind?.name, after, limit + 1)
        Page(
            rows.take(limit).map { it.toUnit() },
            if (rows.size > limit) rows[limit - 1].let { "${it.kind}:${it.code}" } else null,
        )
    }

    override fun save(companyId: UUID, change: UnitChange): Result<MutationReceipt> =
        safeDatabaseCall {
                val version = change.expectedVersion
                val row =
                    if (version == null) source.insert(change.toRow(companyId))
                    else source.update(change.toRow(companyId), version)
                row?.let { MutationReceipt(it.id, it.version) }
            }
            .requireCurrentVersion()
}
