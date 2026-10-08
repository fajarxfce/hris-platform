package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.AttendanceCorrections.ATTENDANCE_CORRECTIONS as C
import dev.fajar.hris.schema.tables.AttendanceDayVersions.ATTENDANCE_DAY_VERSIONS as V
import dev.fajar.hris.schema.tables.records.AttendanceCorrectionsRecord
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresAttendanceCorrectionDataSource(private val sql: DSLContext) :
    AttendanceCorrectionDataSource {
    override fun latest(
        company: UUID,
        employee: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<AttendanceCorrectionsRecord> =
        sql.select(C.asterisk())
            .distinctOn(C.WORK_DATE)
            .from(C)
            .where(C.COMPANY_ID.eq(company))
            .and(C.EMPLOYMENT_ID.eq(employee))
            .and(C.WORK_DATE.between(from, until))
            .orderBy(C.WORK_DATE, C.REVISION.desc())
            .fetchInto(C)

    override fun history(
        company: UUID,
        employee: UUID,
        date: LocalDate,
        after: Long?,
        limit: Int,
    ): List<AttendanceCorrectionsRecord> =
        sql.selectFrom(C)
            .where(C.COMPANY_ID.eq(company))
            .and(C.EMPLOYMENT_ID.eq(employee))
            .and(C.WORK_DATE.eq(date))
            .and(after?.let { C.REVISION.lt(it) } ?: DSL.noCondition())
            .orderBy(C.REVISION.desc())
            .limit(limit)
            .fetch()

    override fun insertVersion(company: UUID, employee: UUID, date: LocalDate) {
        sql.insertInto(V)
            .set(V.COMPANY_ID, company)
            .set(V.EMPLOYMENT_ID, employee)
            .set(V.WORK_DATE, date)
            .set(V.VERSION, 0L)
            .execute()
    }

    override fun advanceVersion(
        company: UUID,
        employee: UUID,
        date: LocalDate,
        expectedVersion: Long,
    ): Long? =
        sql.update(V)
            .set(V.VERSION, expectedVersion + 1)
            .where(V.COMPANY_ID.eq(company))
            .and(V.EMPLOYMENT_ID.eq(employee))
            .and(V.WORK_DATE.eq(date))
            .and(V.VERSION.eq(expectedVersion))
            .returning(V.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: AttendanceCorrectionsRecord) {
        sql.insertInto(C).set(row).execute()
    }
}
