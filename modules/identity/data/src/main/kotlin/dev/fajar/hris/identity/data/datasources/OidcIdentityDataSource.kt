package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface OidcIdentityDataSource {
    fun lockSubject(issuer: String, subject: String)

    fun lockAccount(accountId: UUID): AccountsRecord?

    fun find(id: UUID): OidcIdentitiesRecord?

    fun resolve(issuer: String, subject: String): OidcIdentitiesRecord?

    fun list(accountId: UUID, after: UUID?, size: Int): List<OidcIdentitiesRecord>

    fun activeCount(accountId: UUID): Int

    fun insert(row: OidcIdentitiesRecord)

    fun update(id: UUID, active: Boolean, expectedVersion: Long): Long?

    fun advanceSecurityVersion(accountId: UUID)
}
