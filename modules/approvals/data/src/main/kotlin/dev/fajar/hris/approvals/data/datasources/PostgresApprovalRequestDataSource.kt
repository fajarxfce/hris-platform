package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.Routines
import dev.fajar.hris.schema.tables.Accounts.ACCOUNTS as I
import dev.fajar.hris.schema.tables.ApprovalAssignmentOverrides.APPROVAL_ASSIGNMENT_OVERRIDES as A
import dev.fajar.hris.schema.tables.ApprovalDecisions.APPROVAL_DECISIONS as C
import dev.fajar.hris.schema.tables.ApprovalDelegations.APPROVAL_DELEGATIONS as D
import dev.fajar.hris.schema.tables.ApprovalRequests.APPROVAL_REQUESTS as R
import dev.fajar.hris.schema.tables.CompanyMemberships.COMPANY_MEMBERSHIPS as M
import dev.fajar.hris.schema.tables.MembershipPermissions.MEMBERSHIP_PERMISSIONS as P
import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresApprovalRequestDataSource(private val sql: DSLContext) : ApprovalRequestDataSource {
    override fun lock(companyId: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "approvals:$companyId")
            .execute()
    }

    override fun insert(row: ApprovalRequestsRecord) {
        sql.insertInto(R).set(row).execute()
    }

    override fun find(companyId: UUID, id: UUID): ApprovalRequestsRecord? =
        sql.selectFrom(R).where(R.COMPANY_ID.eq(companyId)).and(R.ID.eq(id)).fetchOne()

    override fun inbox(
        companyId: UUID,
        accountId: UUID,
        includeBlocked: Boolean,
        permissionsByKind: Map<String, Set<String>>,
        at: OffsetDateTime,
        after: UUID?,
        limit: Int,
    ): List<ApprovalRequestsRecord> {
        val assignees = Routines.approvalAssignees(R.COMPANY_ID, R.ID, R.CURRENT_STEP)
        val assigned = DSL.condition("{0} = any({1})", DSL.`val`(accountId), assignees)
        val liveDelegator =
            DSL.exists(
                sql.selectOne()
                    .from(M)
                    .join(I)
                    .on(I.ID.eq(M.ACCOUNT_ID))
                    .join(P)
                    .on(P.COMPANY_ID.eq(M.COMPANY_ID).and(P.ACCOUNT_ID.eq(M.ACCOUNT_ID)))
                    .where(M.COMPANY_ID.eq(R.COMPANY_ID))
                    .and(M.ACCOUNT_ID.eq(D.FROM_ACCOUNT))
                    .and(M.ACTIVE.isTrue)
                    .and(I.ACTIVE.isTrue)
                    .and(
                        DSL.or(
                            permissionsByKind.map { (kind, permissions) ->
                                R.KIND.eq(kind).and(P.PERMISSION.`in`(permissions))
                            }
                        )
                    )
            )
        val delegated =
            DSL.exists(
                sql.selectOne()
                    .from(D)
                    .where(D.COMPANY_ID.eq(R.COMPANY_ID))
                    .and(D.KIND.eq(R.KIND))
                    .and(D.TO_ACCOUNT.eq(accountId))
                    .and(D.ACTIVE.isTrue)
                    .and(liveDelegator)
                    .and(D.VALID_FROM.le(at))
                    .and(D.VALID_UNTIL.gt(at))
                    .and(DSL.condition("{0} = any({1})", D.FROM_ACCOUNT, assignees))
                    .and(
                        DSL.condition(
                            "not ({0} = any({1}))",
                            D.FROM_ACCOUNT,
                            R.EXCLUDED_ACCOUNT_IDS,
                        )
                    )
                    .and(D.FROM_ACCOUNT.ne(R.AUTHOR_ID))
                    .and(R.REQUESTER_ID.isNull.or(D.FROM_ACCOUNT.ne(R.REQUESTER_ID)))
            )
        val eligible =
            R.AUTHOR_ID.ne(accountId)
                .and(R.REQUESTER_ID.isNull.or(R.REQUESTER_ID.ne(accountId)))
                .and(
                    DSL.condition(
                        "not ({0} = any({1}))",
                        DSL.`val`(accountId),
                        R.EXCLUDED_ACCOUNT_IDS,
                    )
                )
        val blocked = if (includeBlocked) R.STATUS.eq("BLOCKED") else DSL.falseCondition()
        return sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.STATUS.`in`("PENDING", "BLOCKED"))
            .and(after?.let { R.ID.gt(it) } ?: DSL.noCondition())
            .and(
                blocked.or(
                    eligible.and(R.KIND.`in`(permissionsByKind.keys)).and(assigned.or(delegated))
                )
            )
            .orderBy(R.ID)
            .limit(limit)
            .fetch()
    }

    override fun latestAssignments(
        companyId: UUID,
        ids: Set<UUID>,
    ): List<ApprovalAssignmentOverridesRecord> =
        sql.select(A.asterisk())
            .distinctOn(A.REQUEST_ID, A.STEP)
            .from(A)
            .where(A.COMPANY_ID.eq(companyId))
            .and(A.REQUEST_ID.`in`(ids))
            .orderBy(A.REQUEST_ID, A.STEP, A.REVISION.desc())
            .fetchInto(A)

    override fun update(
        companyId: UUID,
        id: UUID,
        version: Long,
        status: String,
        step: Int?,
    ): Long? =
        sql.update(R)
            .set(R.STATUS, status)
            .set(R.CURRENT_STEP, step?.let { DSL.`val`(it) } ?: R.CURRENT_STEP)
            .set(R.VERSION, version + 1)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.ID.eq(id))
            .and(R.VERSION.eq(version))
            .returning(R.VERSION)
            .fetchOne()
            ?.version

    override fun appendDecision(row: ApprovalDecisionsRecord) {
        sql.insertInto(C).set(row).execute()
    }

    override fun appendAssignment(row: ApprovalAssignmentOverridesRecord) {
        sql.insertInto(A).set(row).execute()
    }
}
