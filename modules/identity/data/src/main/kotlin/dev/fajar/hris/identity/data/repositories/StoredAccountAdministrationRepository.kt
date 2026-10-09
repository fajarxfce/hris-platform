package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.AccountAdministrationDataSource
import dev.fajar.hris.identity.data.mappers.toManagedAccount
import dev.fajar.hris.identity.domain.entities.ManagedAccount
import dev.fajar.hris.identity.domain.repositories.AccountAdministrationRepository
import java.util.UUID

class StoredAccountAdministrationRepository(private val source: AccountAdministrationDataSource) :
    AccountAdministrationRepository {
    override fun lockAdministration(): Result<Unit> = safeDatabaseCall {
        source.lockAdministration()
    }

    override fun lockAccount(id: UUID): Result<ManagedAccount?> = safeDatabaseCall {
        source.lockAccount(id)?.toManagedAccount()
    }

    override fun list(query: String, after: UUID?, limit: Int): Result<Page<ManagedAccount>> =
        safeDatabaseCall {
            val rows = source.list(query, after, limit + 1)
            val selected = rows.take(limit)
            Page(
                selected.map { it.toManagedAccount() },
                if (rows.size > limit) selected.last().id.toString() else null,
            )
        }

    override fun save(
        id: UUID,
        expectedVersion: Long,
        active: Boolean,
        permissions: Set<String>,
    ): Result<MutationReceipt> =
        safeDatabaseCall { source.update(id, expectedVersion, active) }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.replacePermissions(id, permissions)
                    MutationReceipt(id, version)
                }
            }
}
