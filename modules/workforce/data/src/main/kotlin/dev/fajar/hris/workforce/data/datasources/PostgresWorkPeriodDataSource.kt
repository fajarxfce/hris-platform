package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.Tables.*
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresWorkPeriodDataSource(private val sql: DSLContext) : WorkPeriodDataSource {
    override fun ensure(companyId: UUID, month: LocalDate) {
        sql.insertInto(WORK_PERIODS)
            .set(WORK_PERIODS.COMPANY_ID, companyId)
            .set(WORK_PERIODS.MONTH, month)
            .onConflict(WORK_PERIODS.COMPANY_ID, WORK_PERIODS.MONTH)
            .doNothing()
            .execute()
    }

    override fun find(companyId: UUID, month: LocalDate, lock: String?): WorkPeriodsRecord? {
        val query =
            sql.selectFrom(WORK_PERIODS)
                .where(WORK_PERIODS.COMPANY_ID.eq(companyId), WORK_PERIODS.MONTH.eq(month))
        return when (lock) {
            "UPDATE" -> query.forUpdate().fetchOne()
            "SHARE" -> query.forShare().fetchOne()
            null -> query.fetchOne()
            else -> error("Unknown row lock")
        }
    }

    override fun forJob(companyId: UUID, jobId: UUID, lock: Boolean): WorkPeriodsRecord? {
        val query =
            sql.selectFrom(WORK_PERIODS)
                .where(WORK_PERIODS.COMPANY_ID.eq(companyId), WORK_PERIODS.JOB_ID.eq(jobId))
        return if (lock) query.forUpdate().fetchOne() else query.fetchOne()
    }

    override fun range(
        companyId: UUID,
        from: LocalDate,
        until: LocalDate?,
        lock: Boolean,
    ): List<WorkPeriodsRecord> {
        val query =
            sql.selectFrom(WORK_PERIODS)
                .where(
                    WORK_PERIODS.COMPANY_ID.eq(companyId),
                    WORK_PERIODS.MONTH.ge(from),
                    until?.let { WORK_PERIODS.MONTH.le(it) } ?: DSL.noCondition(),
                )
                .orderBy(WORK_PERIODS.MONTH)
                .limit(1213)
        return if (lock) query.forShare().fetch() else query.fetch()
    }

    override fun update(row: WorkPeriodsRecord, expectedVersion: Long): WorkPeriodsRecord? =
        sql.update(WORK_PERIODS)
            .set(WORK_PERIODS.STATUS, row.status)
            .set(WORK_PERIODS.TIMEZONE, row.timezone)
            .set(WORK_PERIODS.JOB_ID, row.jobId)
            .set(WORK_PERIODS.STARTED_AT, row.startedAt)
            .set(WORK_PERIODS.CLOSED_AT, row.closedAt)
            .set(WORK_PERIODS.FAILURE_CODE, row.failureCode)
            .set(WORK_PERIODS.VERSION, expectedVersion + 1)
            .where(
                WORK_PERIODS.COMPANY_ID.eq(row.companyId),
                WORK_PERIODS.ID.eq(row.id),
                WORK_PERIODS.VERSION.eq(expectedVersion),
            )
            .returning()
            .fetchOne()

    override fun insertTargets(rows: List<WorkPeriodTargetsRecord>) {
        for (chunk in rows.chunked(256)) sql.batchInsert(chunk).execute()
    }

    override fun nextTarget(companyId: UUID, jobId: UUID, after: UUID?): UUID? =
        sql.select(WORK_PERIOD_TARGETS.EMPLOYMENT_ID)
            .from(WORK_PERIOD_TARGETS)
            .where(
                WORK_PERIOD_TARGETS.COMPANY_ID.eq(companyId),
                WORK_PERIOD_TARGETS.JOB_ID.eq(jobId),
                after?.let { WORK_PERIOD_TARGETS.EMPLOYMENT_ID.gt(it) } ?: DSL.noCondition(),
            )
            .orderBy(WORK_PERIOD_TARGETS.EMPLOYMENT_ID)
            .limit(1)
            .fetchOne(WORK_PERIOD_TARGETS.EMPLOYMENT_ID)

    override fun snapshot(
        companyId: UUID,
        jobId: UUID,
        employeeId: UUID,
    ): WorkPeriodSnapshotsRecord? =
        sql.selectFrom(WORK_PERIOD_SNAPSHOTS)
            .where(
                WORK_PERIOD_SNAPSHOTS.COMPANY_ID.eq(companyId),
                WORK_PERIOD_SNAPSHOTS.JOB_ID.eq(jobId),
                WORK_PERIOD_SNAPSHOTS.EMPLOYMENT_ID.eq(employeeId),
            )
            .fetchOne()

    override fun insertSnapshot(row: WorkPeriodSnapshotsRecord) {
        sql.executeInsert(row)
    }

    override fun countSnapshots(companyId: UUID, jobId: UUID): Int =
        sql.fetchCount(
            WORK_PERIOD_SNAPSHOTS,
            WORK_PERIOD_SNAPSHOTS.COMPANY_ID.eq(companyId)
                .and(WORK_PERIOD_SNAPSHOTS.JOB_ID.eq(jobId)),
        )
}
