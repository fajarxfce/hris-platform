package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.MembershipDataSource
import dev.fajar.hris.identity.data.mappers.toMember
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.util.UUID

class StoredMembershipRepository(private val source: MembershipDataSource) : MembershipRepository {
    override fun lock(companyId: UUID): Result<Unit> = safeDatabaseCall { source.lock(companyId) }

    override fun find(companyId: UUID, accountId: UUID): Result<MemberAccount?> = safeDatabaseCall {
        source.find(companyId, accountId)?.toMember()
    }

    override fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<MemberAccount>> =
        safeDatabaseCall {
            val rows = source.list(companyId, after, limit + 1)
            Page(
                rows.take(limit).map { it.toMember() },
                if (rows.size > limit) rows[limit - 1].id.toString() else null,
            )
        }

    override fun candidates(
        companyId: UUID,
        accountIds: Set<UUID>,
        permissions: Set<String>,
        limit: Int,
    ): Result<List<MemberAccount>> = safeDatabaseCall {
        source.candidates(companyId, accountIds, permissions, limit).map { it.toMember() }
    }

    override fun save(
        companyId: UUID,
        accountId: UUID,
        expectedVersion: Long?,
        active: Boolean,
        permissions: Set<String>,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) {
                    source.insert(companyId, accountId, active)
                    0L
                } else source.update(companyId, accountId, expectedVersion, active)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.replacePermissions(companyId, accountId, permissions)
                    MutationReceipt(accountId, version)
                }
            }
}
