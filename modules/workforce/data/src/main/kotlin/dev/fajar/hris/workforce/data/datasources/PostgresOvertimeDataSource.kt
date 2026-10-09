package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.OvertimeChanges.OVERTIME_CHANGES as H
import dev.fajar.hris.schema.tables.OvertimeRequests.OVERTIME_REQUESTS as R
import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresOvertimeDataSource(private val sql: DSLContext) : OvertimeDataSource {
    override fun lock(company: UUID, employee: UUID, shared: Boolean) {
        sql.execute(
            if (shared) "select pg_advisory_xact_lock_shared(hashtextextended(?,0))"
            else "select pg_advisory_xact_lock(hashtextextended(?,0))",
            "overtime:$company:$employee",
        )
    }

    override fun find(company: UUID, id: UUID): OvertimeRequestsRecord? =
        sql.selectFrom(R).where(R.COMPANY_ID.eq(company)).and(R.ID.eq(id)).fetchOne()

    override fun count(company: UUID, employee: UUID, from: LocalDate, until: LocalDate): Int =
        sql.fetchCount(
            sql.selectOne()
                .from(R)
                .where(R.COMPANY_ID.eq(company))
                .and(R.EMPLOYMENT_ID.eq(employee))
                .and(R.WORK_DATE.between(from, until))
                .limit(129)
        )

    override fun overlaps(
        company: UUID,
        employee: UUID,
        startsAt: OffsetDateTime,
        endsAt: OffsetDateTime,
    ): Boolean =
        sql.fetchExists(
            sql.selectOne()
                .from(R)
                .where(R.COMPANY_ID.eq(company))
                .and(R.EMPLOYMENT_ID.eq(employee))
                .and(R.STATUS.`in`("PLANNED", "PENDING", "APPROVED"))
                .and(R.REQUESTED_START.lt(endsAt))
                .and(R.REQUESTED_END.gt(startsAt))
        )

    override fun unresolved(company: UUID, from: LocalDate, until: LocalDate): Boolean =
        sql.fetchExists(
            sql.selectOne()
                .from(R)
                .where(R.COMPANY_ID.eq(company))
                .and(R.WORK_DATE.between(from, until))
                .and(R.STATUS.`in`("PLANNED", "PENDING"))
        )

    override fun approved(
        company: UUID,
        employee: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<OvertimeRequestsRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company))
            .and(R.EMPLOYMENT_ID.eq(employee))
            .and(R.WORK_DATE.between(from, until))
            .and(R.STATUS.eq("APPROVED"))
            .orderBy(R.WORK_DATE, R.REQUESTED_START, R.ID)
            .limit(129)
            .fetch()

    override fun list(
        company: UUID,
        employee: UUID?,
        from: LocalDate,
        until: LocalDate,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<OvertimeRequestsRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(company))
            .and(R.WORK_DATE.between(from, until))
            .and(employee?.let { R.EMPLOYMENT_ID.eq(it) } ?: DSL.noCondition())
            .and(status?.let { R.STATUS.eq(it) } ?: DSL.noCondition())
            .and(after?.let { R.ID.gt(it) } ?: DSL.noCondition())
            .orderBy(R.ID)
            .limit(limit)
            .fetch()

    override fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<OvertimeChangesRecord> =
        sql.selectFrom(H)
            .where(H.COMPANY_ID.eq(company))
            .and(H.REQUEST_ID.eq(id))
            .and(after?.let { H.REVISION.lt(it) } ?: DSL.noCondition())
            .orderBy(H.REVISION.desc())
            .limit(limit)
            .fetch()

    override fun insert(row: OvertimeRequestsRecord) {
        sql.insertInto(R).set(row).execute()
    }

    override fun update(row: OvertimeRequestsRecord, expectedVersion: Long): Long? =
        sql.update(R)
            .set(R.STATUS, row.status)
            .set(R.ACTUAL_START, row.actualStart)
            .set(R.ACTUAL_END, row.actualEnd)
            .set(R.ACTUAL_BREAK, row.actualBreak)
            .set(R.SUBMITTED_BY, row.submittedBy)
            .set(R.SUBMITTED_AT, row.submittedAt)
            .set(R.APPROVAL_ID, row.approvalId)
            .set(R.APPROVED_MINUTES, row.approvedMinutes)
            .set(R.DECIDED_AT, row.decidedAt)
            .set(R.VERSION, expectedVersion + 1)
            .where(R.COMPANY_ID.eq(row.companyId))
            .and(R.ID.eq(row.id))
            .and(R.VERSION.eq(expectedVersion))
            .returning(R.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: OvertimeChangesRecord) {
        sql.insertInto(H).set(row).execute()
    }
}
