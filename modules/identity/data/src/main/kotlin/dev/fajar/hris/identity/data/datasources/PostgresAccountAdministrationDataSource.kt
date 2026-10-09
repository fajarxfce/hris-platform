package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.identity.data.queries.*
import dev.fajar.hris.schema.Tables.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresAccountAdministrationDataSource(private val sql: DSLContext) :
    AccountAdministrationDataSource {
    override fun lockAdministration() {
        sql.query("select pg_advisory_xact_lock(hashtextextended('identity-administration',0))")
            .execute()
    }

    override fun lockAccount(id: UUID): AccountAdministrationRow? {
        val locked =
            sql.select(ACCOUNTS.ID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.ID.eq(id))
                .forNoKeyUpdate()
                .fetchOne()
        if (locked == null) return null
        // Read permissions in a fresh snapshot after a possible wait for the row guard.
        return selectManagedAccounts(sql).where(ACCOUNTS.ID.eq(id)).fetchOne {
            it.toAccountAdministrationRow()
        }
    }

    override fun list(query: String, after: UUID?, limit: Int): List<AccountAdministrationRow> =
        selectManagedAccounts(sql)
            .where(
                ACCOUNTS.EMAIL.contains(query).or(ACCOUNTS.DISPLAY_NAME.containsIgnoreCase(query))
            )
            .and(after?.let { ACCOUNTS.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(ACCOUNTS.ID)
            .limit(limit)
            .fetch { it.toAccountAdministrationRow() }

    override fun update(id: UUID, expectedVersion: Long, active: Boolean): Long? =
        sql.update(ACCOUNTS)
            .set(ACCOUNTS.ACTIVE, active)
            .set(ACCOUNTS.INVITATION_PENDING, false)
            .set(ACCOUNTS.VERSION, expectedVersion + 1)
            .set(ACCOUNTS.SECURITY_VERSION, ACCOUNTS.SECURITY_VERSION.plus(1))
            .where(ACCOUNTS.ID.eq(id).and(ACCOUNTS.VERSION.eq(expectedVersion)))
            .returning(ACCOUNTS.VERSION)
            .fetchOne()
            ?.version

    override fun replacePermissions(id: UUID, permissions: Set<String>) {
        sql.deleteFrom(PLATFORM_PERMISSIONS).where(PLATFORM_PERMISSIONS.ACCOUNT_ID.eq(id)).execute()
        if (permissions.isNotEmpty())
            sql.batch(
                    permissions.map { permission ->
                        sql.insertInto(PLATFORM_PERMISSIONS)
                            .set(PLATFORM_PERMISSIONS.ACCOUNT_ID, id)
                            .set(PLATFORM_PERMISSIONS.PERMISSION, permission)
                    }
                )
                .execute()
    }
}
