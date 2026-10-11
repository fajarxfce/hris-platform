package dev.fajar.hris.communications.data.repositories

import dev.fajar.hris.communications.data.datasources.AudienceReferenceDataSource
import dev.fajar.hris.communications.data.mappers.toReference
import dev.fajar.hris.communications.data.models.AudienceReferenceQuery
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.AudienceReferenceRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID

class StoredAudienceReferenceRepository(private val source: AudienceReferenceDataSource) :
    AudienceReferenceRepository {
    override fun list(
        companyId: UUID,
        search: AudienceReferenceSearch,
        activeOnly: Boolean,
    ): Result<Page<AudienceReference>> = safeDatabaseCall {
        val query =
            AudienceReferenceQuery(
                companyId,
                search.query,
                search.ids.toSet(),
                search.after,
                search.limit + 1,
                if (activeOnly) true else null,
            )
        val rows =
            when (search.kind) {
                AudienceReferenceKind.BRANCH,
                AudienceReferenceKind.DEPARTMENT -> source.units(query, search.kind.name)
                AudienceReferenceKind.GROUP -> source.groups(query)
                AudienceReferenceKind.EMPLOYMENT -> source.employments(query)
            }
        Page(
            rows.take(search.limit).map { it.toReference(search.kind) },
            if (rows.size > search.limit) rows[search.limit - 1].id.toString() else null,
        )
    }
}
