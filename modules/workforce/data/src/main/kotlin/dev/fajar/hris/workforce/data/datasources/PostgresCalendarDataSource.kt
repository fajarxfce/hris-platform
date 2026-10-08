package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.RosterDays.ROSTER_DAYS as R
import dev.fajar.hris.schema.tables.RosterRevisions.ROSTER_REVISIONS as H
import dev.fajar.hris.schema.tables.ScheduleAssignments.SCHEDULE_ASSIGNMENTS as A
import dev.fajar.hris.schema.tables.ScheduleVersions.SCHEDULE_VERSIONS as V
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext

class PostgresCalendarDataSource(private val sql: DSLContext) : CalendarDataSource {
    override fun lock(companyId: UUID) {
        sql.query("select pg_advisory_xact_lock(hashtextextended(?,0))", "calendar:$companyId")
            .execute()
    }

    override fun scheduleVersion(companyId: UUID, employeeId: UUID): Long? =
        sql.select(V.VERSION)
            .from(V)
            .where(V.COMPANY_ID.eq(companyId))
            .and(V.EMPLOYMENT_ID.eq(employeeId))
            .fetchOne(V.VERSION)

    override fun insertScheduleVersion(companyId: UUID, employeeId: UUID) {
        sql.insertInto(V).set(V.COMPANY_ID, companyId).set(V.EMPLOYMENT_ID, employeeId).execute()
    }

    override fun advanceScheduleVersion(
        companyId: UUID,
        employeeId: UUID,
        expectedVersion: Long,
    ): Long? =
        sql.update(V)
            .set(V.VERSION, expectedVersion + 1)
            .where(V.COMPANY_ID.eq(companyId))
            .and(V.EMPLOYMENT_ID.eq(employeeId))
            .and(V.VERSION.eq(expectedVersion))
            .returning(V.VERSION)
            .fetchOne()
            ?.version

    override fun appendAssignment(row: ScheduleAssignmentsRecord) {
        sql.insertInto(A).set(row).execute()
    }

    override fun assignments(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<ScheduleAssignmentsRecord> {
        val previous = A.`as`("previous")
        val baseline =
            sql.select(previous.REVISION)
                .from(previous)
                .where(previous.COMPANY_ID.eq(companyId))
                .and(previous.EMPLOYMENT_ID.eq(employeeId))
                .and(previous.EFFECTIVE_FROM.lt(from))
                .orderBy(previous.EFFECTIVE_FROM.desc(), previous.REVISION.desc())
                .limit(1)
        return sql.select(A.asterisk())
            .distinctOn(A.EFFECTIVE_FROM)
            .from(A)
            .where(A.COMPANY_ID.eq(companyId))
            .and(A.EMPLOYMENT_ID.eq(employeeId))
            .and(A.EFFECTIVE_FROM.le(until))
            .and(A.EFFECTIVE_FROM.ge(from).or(A.REVISION.eq(baseline)))
            .orderBy(A.EFFECTIVE_FROM, A.REVISION.desc())
            .fetchInto(A)
    }

    override fun roster(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<RosterDaysRecord> =
        sql.selectFrom(R)
            .where(R.COMPANY_ID.eq(companyId))
            .and(R.EMPLOYMENT_ID.eq(employeeId))
            .and(R.WORK_DATE.between(from, until))
            .orderBy(R.WORK_DATE)
            .fetch()

    override fun insertRoster(row: RosterDaysRecord) {
        sql.insertInto(R).set(row).execute()
    }

    override fun updateRoster(row: RosterDaysRecord, expectedVersion: Long): Long? =
        sql.update(R)
            .set(R.SHIFT, row.shift)
            .set(R.VERSION, expectedVersion + 1)
            .where(R.COMPANY_ID.eq(row.companyId))
            .and(R.EMPLOYMENT_ID.eq(row.employmentId))
            .and(R.WORK_DATE.eq(row.workDate))
            .and(R.VERSION.eq(expectedVersion))
            .returning(R.VERSION)
            .fetchOne()
            ?.version

    override fun appendRoster(row: RosterRevisionsRecord) {
        sql.insertInto(H).set(row).execute()
    }
}
