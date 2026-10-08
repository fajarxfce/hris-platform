package dev.fajar.hris.identity.data.datasources

import java.util.UUID

interface AccountAdministrationDataSource {
    fun lockAdministration()

    fun lockAccount(id: UUID): AccountAdministrationRow?

    fun list(query: String, after: UUID?, limit: Int): List<AccountAdministrationRow>

    fun permissions(ids: Set<UUID>): Map<UUID, Set<String>>

    fun update(id: UUID, expectedVersion: Long, active: Boolean): Long?

    fun replacePermissions(id: UUID, permissions: Set<String>)
}
