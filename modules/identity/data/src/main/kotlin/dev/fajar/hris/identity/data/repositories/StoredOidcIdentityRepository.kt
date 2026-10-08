package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.OidcIdentityDataSource
import dev.fajar.hris.identity.data.mappers.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.OidcIdentityRepository
import java.util.UUID

class StoredOidcIdentityRepository(private val source: OidcIdentityDataSource) :
    OidcIdentityRepository {
    override fun lockSubject(issuer: String, subject: String): Result<Unit> = safeDatabaseCall {
        source.lockSubject(issuer, subject)
    }

    override fun lockAccount(accountId: UUID): Result<Account?> = safeDatabaseCall {
        source.lockAccount(accountId)?.toAccount()
    }

    override fun find(id: UUID): Result<OidcIdentity?> = safeDatabaseCall {
        source.find(id)?.toIdentity()
    }

    override fun resolve(issuer: String, subject: String): Result<OidcIdentity?> =
        safeDatabaseCall {
            source.resolve(issuer, subject)?.toIdentity()
        }

    override fun list(accountId: UUID, after: UUID?, size: Int): Result<Page<OidcIdentity>> =
        safeDatabaseCall {
            val rows = source.list(accountId, after, size + 1)
            Page(
                rows.take(size).map { it.toIdentity() },
                if (rows.size > size) rows[size - 1].id.toString() else null,
            )
        }

    override fun activeCount(accountId: UUID): Result<Int> = safeDatabaseCall {
        source.activeCount(accountId)
    }

    override fun save(identity: OidcIdentity, expectedVersion: Long?): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) {
                    source.insert(identity.toRow())
                    0L
                } else source.update(identity.id, identity.active, expectedVersion)
            }
            .flatMap { version ->
                version?.let { Result.Success(MutationReceipt(identity.id, it)) }
                    ?: Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }

    override fun advanceSecurityVersion(accountId: UUID): Result<Unit> = safeDatabaseCall {
        source.advanceSecurityVersion(accountId)
    }
}
