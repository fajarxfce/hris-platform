package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.records.AccountsRecord
import java.util.UUID

interface IdentityDataSource {
    fun lockCompany(companyId: UUID, shared: Boolean)

    fun lockAccount(id: UUID, shared: Boolean = false)

    fun lockBootstrap()

    fun countAccounts(): Int

    fun findByEmail(email: String): AccountsRecord?

    fun findById(id: UUID): AccountsRecord?

    fun insertAccount(id: UUID, email: String, name: String, passwordHash: String): AccountsRecord

    fun activeMembershipPermissions(accountId: UUID): Set<String>

    fun platformPermissions(accountId: UUID): Set<String>

    fun companyPermissions(accountId: UUID, companyId: UUID): CompanyPermissionRow?

    fun memberships(accountId: UUID): List<MembershipRow>

    fun insertPlatformPermissions(accountId: UUID, permissions: Set<String>)

    fun insertMembership(accountId: UUID, companyId: UUID, permissions: Set<String>)
}
