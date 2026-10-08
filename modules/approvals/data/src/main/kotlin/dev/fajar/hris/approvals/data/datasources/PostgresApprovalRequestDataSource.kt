package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.Routines
import dev.fajar.hris.schema.tables.ApprovalAssignmentOverrides.APPROVAL_ASSIGNMENT_OVERRIDES as A
import dev.fajar.hris.schema.tables.ApprovalDecisions.APPROVAL_DECISIONS as C
import dev.fajar.hris.schema.tables.ApprovalDelegations.APPROVAL_DELEGATIONS as D
import dev.fajar.hris.schema.tables.ApprovalRequests.APPROVAL_REQUESTS as R
import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresApprovalRequestDataSource(private val sql: DSLContext) : ApprovalRequestDataSource {
    override fun insert(row: ApprovalRequestsRecord) {
        sql.insertInto(R).set(row).execute()
    }

    override fun find(companyId: UUID, id: UUID): ApprovalRequestsRecord? =
        sql.selectFrom(R).where(R.COMPANY_ID.eq(companyId)).and(R.ID.eq(id)).fetchOne()

    override fun inbox(
        companyId: UUID,
        accountId: UUID,
        includeBlocked: Boolean,
        at: OffsetDateTime,
        after: UUID?,
        limit: Int,
    ): List<ApprovalRequestsRecord> {
        val assignees = Routines.approvalAssignees(R.COMPANY_ID, R.ID, R.CURRENT_STEP)
        val assigned = DSL.condition("{0} = any({1})", DSL.`val`(accountId), assignees)
        val delegated =
            DSL.exists(
                sql.selectOne()
                    .from(D)
                    .where(D.COMPANY_ID.eq(R.COMPANY_ID))
                    .and(D.KIND.eq(R.KIND))
                    .and(D.TO_ACCOUNT.eq(accountId))
                    .and(D.ACTIVE.isTrue)
                    .and(D.VALID_FROM.le(at))
                    .and(D.VALID_UNTIL.gt(at))
                    .and(DSL.condition("{0} = any({1})", D.FROM_ACCOUNT, assignees))
            )
        val blocked = if (includeBlocked) R.STATUS.eq("BLOCKED") else DSL.falseCondition()
        return sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.STATUS.`in`("PENDING", "BLOCKED"))
            .and(after?.let { R.ID.gt(it) } ?: DSL.noCondition())
            .and(blocked.or(assigned).or(delegated))
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
