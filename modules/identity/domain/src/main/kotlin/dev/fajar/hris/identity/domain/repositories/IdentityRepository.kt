package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.entities.Account
import dev.fajar.hris.identity.domain.entities.AccountAccess
import dev.fajar.hris.identity.domain.entities.CompanyMembership
import java.util.UUID

interface IdentityRepository {
    /** Shares the company-state guard with settings changes without owning company settings. */
    fun lockCompany(companyId: UUID, shared: Boolean = false): Result<Unit>

    fun lockAccount(accountId: UUID, shared: Boolean = false): Result<Unit>

    fun lockBootstrap(): Result<Unit>

    fun hasAccounts(): Result<Boolean>

    fun createAccount(
        id: UUID,
        email: String,
        displayName: String,
        password: String,
    ): Result<Account>

    fun verifyPassword(email: String, password: String): Result<Account?>

    fun access(accountId: UUID, companyId: UUID?): Result<AccountAccess?>

    fun memberships(accountId: UUID): Result<List<CompanyMembership>>

    fun grantPlatformPermissions(accountId: UUID, permissions: Set<String>): Result<Unit>

    fun grantMembership(accountId: UUID, companyId: UUID, permissions: Set<String>): Result<Unit>
}
