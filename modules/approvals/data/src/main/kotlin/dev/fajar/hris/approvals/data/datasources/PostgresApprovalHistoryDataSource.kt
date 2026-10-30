package dev.fajar.hris.approvals.data.datasources

import dev.fajar.hris.schema.tables.ApprovalAssignmentOverrides.APPROVAL_ASSIGNMENT_OVERRIDES as A
import dev.fajar.hris.schema.tables.ApprovalDecisions.APPROVAL_DECISIONS as D
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresApprovalHistoryDataSource(private val sql: DSLContext) : ApprovalHistoryDataSource {
    override fun decisions(companyId: UUID, id: UUID, limit: Int) =
        sql.selectFrom(D)
            .where(D.COMPANY_ID.eq(companyId), D.REQUEST_ID.eq(id))
            .orderBy(D.STEP)
            .limit(limit)
            .fetch()

    override fun reassignments(companyId: UUID, id: UUID, after: Long?, limit: Int) =
        sql.selectFrom(A)
            .where(
                A.COMPANY_ID.eq(companyId),
                A.REQUEST_ID.eq(id),
                after?.let { A.REVISION.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(A.REVISION)
            .limit(limit)
            .fetch()
}
