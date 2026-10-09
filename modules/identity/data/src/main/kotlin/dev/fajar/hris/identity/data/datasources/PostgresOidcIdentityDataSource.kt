package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.Tables.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresOidcIdentityDataSource(private val sql: DSLContext) : OidcIdentityDataSource {
    override fun lockSubject(issuer: String, subject: String) {
        sql.execute("select pg_advisory_xact_lock(hashtextextended(?,0))", "oidc:$issuer:$subject")
    }

    override fun lockAccount(accountId: UUID): AccountsRecord? =
        sql.selectFrom(ACCOUNTS).where(ACCOUNTS.ID.eq(accountId)).forNoKeyUpdate().fetchOne()

    override fun find(id: UUID): OidcIdentitiesRecord? =
        sql.selectFrom(OIDC_IDENTITIES).where(OIDC_IDENTITIES.ID.eq(id)).fetchOne()

    override fun resolve(issuer: String, subject: String): OidcIdentitiesRecord? =
        sql.selectFrom(OIDC_IDENTITIES)
            .where(
                OIDC_IDENTITIES.ISSUER.eq(issuer),
                OIDC_IDENTITIES.SUBJECT.eq(subject),
                OIDC_IDENTITIES.ACTIVE.isTrue,
            )
            .fetchOne()

    override fun list(accountId: UUID, after: UUID?, size: Int): List<OidcIdentitiesRecord> =
        sql.selectFrom(OIDC_IDENTITIES)
            .where(
                OIDC_IDENTITIES.ACCOUNT_ID.eq(accountId),
                after?.let { OIDC_IDENTITIES.ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(OIDC_IDENTITIES.ID)
            .limit(size)
            .fetch()

    override fun activeCount(accountId: UUID): Int =
        sql.fetchCount(
            OIDC_IDENTITIES,
            OIDC_IDENTITIES.ACCOUNT_ID.eq(accountId).and(OIDC_IDENTITIES.ACTIVE.isTrue),
        )

    override fun insert(row: OidcIdentitiesRecord) {
        sql.executeInsert(row)
    }

    override fun update(id: UUID, active: Boolean, expectedVersion: Long): Long? =
        sql.update(OIDC_IDENTITIES)
            .set(OIDC_IDENTITIES.ACTIVE, active)
            .set(OIDC_IDENTITIES.VERSION, expectedVersion + 1)
            .where(OIDC_IDENTITIES.ID.eq(id), OIDC_IDENTITIES.VERSION.eq(expectedVersion))
            .returning(OIDC_IDENTITIES.VERSION)
            .fetchOne()
            ?.version

    override fun advanceSecurityVersion(accountId: UUID) {
        sql.update(ACCOUNTS)
            .set(ACCOUNTS.SECURITY_VERSION, ACCOUNTS.SECURITY_VERSION.plus(1))
            .set(ACCOUNTS.VERSION, ACCOUNTS.VERSION.plus(1))
            .where(ACCOUNTS.ID.eq(accountId))
            .execute()
    }
}
