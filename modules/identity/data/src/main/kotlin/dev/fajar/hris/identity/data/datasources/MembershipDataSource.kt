package dev.fajar.hris.identity.data.datasources

import java.util.UUID

interface MembershipDataSource {
    fun hasOtherActiveMember(companyId: UUID, exceptAccountId: UUID, permission: String): Boolean

    fun lock(companyId: UUID)

    fun find(companyId: UUID, accountId: UUID): MemberRow?

    fun list(companyId: UUID, after: UUID?, limit: Int): List<MemberRow>

    fun candidates(
        companyId: UUID,
        accountIds: Set<UUID>,
        permissions: Set<String>,
        limit: Int,
    ): List<MemberRow>

    fun insert(companyId: UUID, accountId: UUID, active: Boolean)

    fun update(companyId: UUID, accountId: UUID, expectedVersion: Long, active: Boolean): Long?

    fun replacePermissions(companyId: UUID, accountId: UUID, permissions: Set<String>)
}
