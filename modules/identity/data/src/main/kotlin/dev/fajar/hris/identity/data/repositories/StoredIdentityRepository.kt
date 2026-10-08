package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.domain.flatMap
import dev.fajar.hris.identity.data.datasources.IdentityDataSource
import dev.fajar.hris.identity.data.datasources.PasswordDataSource
import dev.fajar.hris.identity.data.mappers.toAccount
import dev.fajar.hris.identity.domain.entities.Account
import dev.fajar.hris.identity.domain.entities.AccountAccess
import dev.fajar.hris.identity.domain.entities.CompanyMembership
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.util.UUID

class StoredIdentityRepository(
    private val source: IdentityDataSource,
    private val passwords: PasswordDataSource,
    private val dummyHash: String,
) : IdentityRepository {
    override fun lockBootstrap(): Result<Unit> = safeDatabaseCall { source.lockBootstrap() }

    override fun hasAccounts(): Result<Boolean> = safeDatabaseCall { source.countAccounts() != 0 }

    override fun createAccount(
        id: UUID,
        email: String,
        displayName: String,
        password: String,
    ): Result<Account> =
        safeDatabaseCall {
                passwords.hash(password)?.let { hash ->
                    source.insertAccount(id, email, displayName, hash).toAccount()
                }
            }
            .flatMap { account ->
                if (account == null)
                    Result.Failed(Failure(FailureKind.UNEXPECTED, "password_encoding_failed"))
                else Result.Success(account)
            }

    override fun verifyPassword(email: String, password: String): Result<Account?> =
        safeDatabaseCall {
            val account = source.findByEmail(email)
            val matches = passwords.matches(password, account?.passwordHash ?: dummyHash)
            if (matches && account?.passwordHash != null) account.toAccount() else null
        }

    override fun access(accountId: UUID, companyId: UUID?): Result<AccountAccess?> =
        safeDatabaseCall {
            val account = source.findById(accountId)
            if (account == null) null
            else {
                val companyPermissions = companyId?.let { source.companyPermissions(accountId, it) }
                val permissions =
                    if (companyId == null) source.platformPermissions(accountId)
                    else companyPermissions?.permissions.orEmpty()
                AccountAccess(
                    account.toAccount(),
                    permissions,
                    companyId == null || companyPermissions?.memberActive == true,
                    companyId == null || companyPermissions?.companyActive == true,
                )
            }
        }

    override fun memberships(accountId: UUID): Result<List<CompanyMembership>> = safeDatabaseCall {
        source.memberships(accountId).map {
            CompanyMembership(
                it.companyId,
                it.name,
                it.code,
                it.timezone,
                it.memberActive,
                it.companyActive,
            )
        }
    }

    override fun grantPlatformPermissions(accountId: UUID, permissions: Set<String>): Result<Unit> =
        safeDatabaseCall {
            source.insertPlatformPermissions(accountId, permissions)
        }

    override fun grantMembership(
        accountId: UUID,
        companyId: UUID,
        permissions: Set<String>,
    ): Result<Unit> = safeDatabaseCall {
        source.insertMembership(accountId, companyId, permissions)
    }
}
