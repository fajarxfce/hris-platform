package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceDataSource
import dev.fajar.hris.collaboration.domain.entities.CollaborationContactCompany
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceContact
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID

class StoredCollaborationSpaceRepository(private val source: CollaborationSpaceDataSource) :
    CollaborationSpaceRepository {
    override fun current(): Result<UUID?> = safeDatabaseCall { source.current() }

    override fun contacts(
        spaceId: UUID,
        excludedAccountId: UUID,
        query: String,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationSpaceContact>> {
        if (limit !in 1..50 || query.length > 80 || query.any(Char::isISOControl))
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_contact_query")
            )
        return safeDatabaseCall {
            val rows = source.contacts(spaceId, excludedAccountId, query, after, limit + 1, 9)
            require(rows.size <= (limit + 1) * 9)
            require(rows.all { it.spaceId == spaceId && it.accountId != excludedAccountId })
            val accounts = rows.groupBy { requireNotNull(it.accountId) }
            require(accounts.size <= limit + 1)
            val items =
                accounts.entries.take(limit).map { (id, affiliations) ->
                    val name = requireNotNull(affiliations.first().displayName)
                    require(name.isNotBlank() && name.length <= 200)
                    require(affiliations.size <= 9 && affiliations.all { it.displayName == name })
                    val companies =
                        affiliations.map {
                            val label = requireNotNull(it.companyLabel)
                            require(label.isNotBlank() && label.length <= 200)
                            CollaborationContactCompany(requireNotNull(it.companyId), label)
                        }
                    require(companies.map { it.id }.distinct().size == companies.size)
                    CollaborationSpaceContact(id, name, companies.take(8), companies.size > 8)
                }
            Page(items, if (accounts.size > limit) items.last().accountId.toString() else null)
        }
    }
}
