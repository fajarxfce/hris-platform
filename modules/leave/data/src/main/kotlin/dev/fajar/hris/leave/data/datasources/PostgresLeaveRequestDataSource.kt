package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.leave.data.models.LeaveRequestSummaryRow
import dev.fajar.hris.schema.tables.LeaveRequestChanges.LEAVE_REQUEST_CHANGES as H
import dev.fajar.hris.schema.tables.LeaveRequests.LEAVE_REQUESTS as R
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresLeaveRequestDataSource(private val sql: DSLContext) : LeaveRequestDataSource {
    override fun unresolved(
        company: UUID,
        employee: UUID,
        type: UUID,
        from: java.time.LocalDate,
        until: java.time.LocalDate,
        statuses: Set<String>,
    ): Boolean =
        sql.fetchExists(
            sql.selectOne()
                .from(R)
                .where(R.COMPANY_ID.eq(company))
                .and(R.EMPLOYMENT_ID.eq(employee))
                .and(R.TYPE_ID.eq(type))
                .and(R.STARTS_ON.le(until))
                .and(R.ENDS_ON.ge(from))
                .and(R.STATUS.`in`(statuses))
        )

    override fun find(company: UUID, id: UUID): LeaveRequestsRecord? =
        sql.selectFrom(R).where(R.COMPANY_ID.eq(company)).and(R.ID.eq(id)).fetchOne()

    override fun list(
        company: UUID,
        employee: UUID?,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<LeaveRequestSummaryRow> {
        val cursor = R.`as`("cursor")
        val before =
            if (after == null) DSL.noCondition()
            else
                DSL.row(R.SUBMITTED_AT, R.ID)
                    .lt(
                        DSL.select(cursor.SUBMITTED_AT, cursor.ID)
                            .from(cursor)
                            .where(cursor.COMPANY_ID.eq(company))
                            .and(cursor.ID.eq(after))
                            .and(employee?.let { cursor.EMPLOYMENT_ID.eq(it) } ?: DSL.noCondition())
                    )
        return sql.select(
                R.ID,
                R.EMPLOYMENT_ID,
                R.EMPLOYEE_NUMBER,
                R.EMPLOYEE_NAME,
                R.TYPE_CODE,
                R.TYPE_NAME,
                R.STARTS_ON,
                R.ENDS_ON,
                R.HALF_DAYS,
                R.STATUS,
                R.SUBMITTED_AT,
                R.VERSION,
            )
            .from(R)
            .where(R.COMPANY_ID.eq(company))
            .and(employee?.let { R.EMPLOYMENT_ID.eq(it) } ?: DSL.noCondition())
            .and(status?.let { R.STATUS.eq(it) } ?: DSL.noCondition())
            .and(before)
            .orderBy(R.SUBMITTED_AT.desc(), R.ID.desc())
            .limit(limit)
            .fetch {
                LeaveRequestSummaryRow(
                    it[R.ID],
                    it[R.EMPLOYMENT_ID],
                    it[R.EMPLOYEE_NUMBER],
                    it[R.EMPLOYEE_NAME],
                    it[R.TYPE_CODE],
                    it[R.TYPE_NAME],
                    it[R.STARTS_ON],
                    it[R.ENDS_ON],
                    it[R.HALF_DAYS],
                    it[R.STATUS],
                    it[R.SUBMITTED_AT],
                    it[R.VERSION],
                )
            }
    }

    override fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<LeaveRequestChangesRecord> =
        sql.selectFrom(H)
            .where(H.COMPANY_ID.eq(company))
            .and(H.REQUEST_ID.eq(id))
            .and(after?.let { H.REVISION.lt(it) } ?: DSL.noCondition())
            .orderBy(H.REVISION.desc())
            .limit(limit)
            .fetch()

    override fun insert(row: LeaveRequestsRecord) {
        sql.insertInto(R).set(row).execute()
    }

    override fun update(
        company: UUID,
        id: UUID,
        expectedVersion: Long,
        status: String,
        cancellationId: UUID?,
    ): Long? =
        sql.update(R)
            .set(R.VERSION, expectedVersion + 1)
            .set(R.STATUS, status)
            .set(R.CANCELLATION_APPROVAL_ID, cancellationId)
            .where(R.COMPANY_ID.eq(company))
            .and(R.ID.eq(id))
            .and(R.VERSION.eq(expectedVersion))
            .returning(R.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: LeaveRequestChangesRecord) {
        sql.insertInto(H).set(row).execute()
    }
}
