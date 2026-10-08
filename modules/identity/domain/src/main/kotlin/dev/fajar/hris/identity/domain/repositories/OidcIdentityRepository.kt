package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import java.util.UUID

interface OidcIdentityRepository {
    fun lockSubject(issuer: String, subject: String): Result<Unit>

    fun lockAccount(accountId: UUID): Result<Account?>

    fun find(id: UUID): Result<OidcIdentity?>

    fun resolve(issuer: String, subject: String): Result<OidcIdentity?>

    fun list(accountId: UUID, after: UUID?, size: Int): Result<Page<OidcIdentity>>

    fun activeCount(accountId: UUID): Result<Int>

    fun save(identity: OidcIdentity, expectedVersion: Long?): Result<MutationReceipt>

    fun advanceSecurityVersion(accountId: UUID): Result<Unit>
}
