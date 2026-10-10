package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.identity.data.queries.*
import dev.fajar.hris.schema.tables.Accounts.ACCOUNTS as A
import dev.fajar.hris.schema.tables.CompanyMemberships.COMPANY_MEMBERSHIPS as M
import dev.fajar.hris.schema.tables.MembershipPermissions.MEMBERSHIP_PERMISSIONS as P
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresMembershipDataSource(private val sql: DSLContext) : MembershipDataSource {
    override fun hasOtherActiveMember(
        companyId: UUID,
        exceptAccountId: UUID,
        permission: String,
    ): Boolean =
        sql.fetchExists(
            sql.selectOne()
                .from(M)
                .join(A)
                .on(A.ID.eq(M.ACCOUNT_ID))
                .join(P)
                .on(P.COMPANY_ID.eq(M.COMPANY_ID).and(P.ACCOUNT_ID.eq(M.ACCOUNT_ID)))
                .where(M.COMPANY_ID.eq(companyId))
                .and(M.ACCOUNT_ID.ne(exceptAccountId))
                .and(M.ACTIVE.isTrue)
                .and(A.ACTIVE.isTrue)
                .and(P.PERMISSION.eq(permission))
        )

    override fun lock(companyId: UUID, shared: Boolean) {
        sql.query(
                if (shared) "select pg_advisory_xact_lock_shared(hashtextextended(?,0))"
                else "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "memberships:$companyId",
            )
            .execute()
    }

    override fun find(companyId: UUID, accountId: UUID): MemberRow? =
        selectCompanyMembers(sql, companyId).and(A.ID.eq(accountId)).fetchOne { it.toMemberRow() }

    override fun list(companyId: UUID, after: UUID?, limit: Int): List<MemberRow> =
        selectCompanyMembers(sql, companyId)
            .and(after?.let { A.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(A.ID)
            .limit(limit)
            .fetch { it.toMemberRow() }

    override fun activeReferences(
        companyId: UUID,
        permissions: Set<String>,
        query: String,
        after: UUID?,
        limit: Int,
    ): List<MemberReferenceRow> =
        sql.select(A.ID, A.DISPLAY_NAME)
            .from(M)
            .join(A)
            .on(A.ID.eq(M.ACCOUNT_ID))
            .where(M.COMPANY_ID.eq(companyId))
            .and(M.ACTIVE.isTrue)
            .and(A.ACTIVE.isTrue)
            .and(A.DISPLAY_NAME.containsIgnoreCase(query))
            .and(after?.let { A.ID.gt(it) } ?: DSL.noCondition())
            .and(
                DSL.exists(
                    sql.selectOne()
                        .from(P)
                        .where(P.COMPANY_ID.eq(M.COMPANY_ID))
                        .and(P.ACCOUNT_ID.eq(A.ID))
                        .and(P.PERMISSION.`in`(permissions))
                )
            )
            .orderBy(A.ID)
            .limit(limit)
            .fetch { MemberReferenceRow(it.value1(), it.value2()) }

    override fun candidates(
        companyId: UUID,
        accountIds: Set<UUID>,
        permissions: Set<String>,
        limit: Int,
    ): List<MemberRow> =
        selectCompanyMembers(sql, companyId)
            .and(
                A.ID.`in`(accountIds)
                    .or(
                        DSL.exists(
                            sql.selectOne()
                                .from(P)
                                .where(P.COMPANY_ID.eq(companyId))
                                .and(P.ACCOUNT_ID.eq(A.ID))
                                .and(P.PERMISSION.`in`(permissions))
                        )
                    )
            )
            .orderBy(A.ID)
            .limit(limit)
            .fetch { it.toMemberRow() }

    override fun insert(companyId: UUID, accountId: UUID, active: Boolean) {
        sql.insertInto(M)
            .set(M.COMPANY_ID, companyId)
            .set(M.ACCOUNT_ID, accountId)
            .set(M.ACTIVE, active)
            .execute()
    }

    override fun update(
        companyId: UUID,
        accountId: UUID,
        expectedVersion: Long,
        active: Boolean,
    ): Long? =
        sql.update(M)
            .set(M.ACTIVE, active)
            .set(M.VERSION, expectedVersion + 1)
            .where(M.COMPANY_ID.eq(companyId))
            .and(M.ACCOUNT_ID.eq(accountId))
            .and(M.VERSION.eq(expectedVersion))
            .returning(M.VERSION)
            .fetchOne()
            ?.version

    override fun replacePermissions(companyId: UUID, accountId: UUID, permissions: Set<String>) {
        sql.deleteFrom(P)
            .where(P.COMPANY_ID.eq(companyId))
            .and(P.ACCOUNT_ID.eq(accountId))
            .execute()
        if (permissions.isNotEmpty())
            sql.batch(
                    permissions.map { permission ->
                        sql.insertInto(P)
                            .set(P.COMPANY_ID, companyId)
                            .set(P.ACCOUNT_ID, accountId)
                            .set(P.PERMISSION, permission)
                    }
                )
                .execute()
    }
}
