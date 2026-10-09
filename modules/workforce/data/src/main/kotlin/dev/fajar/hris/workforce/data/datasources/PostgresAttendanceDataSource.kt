package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.AttendanceCaptureWindows.ATTENDANCE_CAPTURE_WINDOWS as W
import dev.fajar.hris.schema.tables.AttendanceEvents.ATTENDANCE_EVENTS as E
import dev.fajar.hris.schema.tables.AttendanceReviews.ATTENDANCE_REVIEWS as R
import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.workforce.data.models.AttendanceRow
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresAttendanceDataSource(private val sql: DSLContext) : AttendanceDataSource {
    override fun lockDay(company: UUID, employee: UUID, date: LocalDate, shared: Boolean) {
        val function = if (shared) "pg_advisory_xact_lock_shared" else "pg_advisory_xact_lock"
        sql.query("select $function(hashtextextended(?,0))", "attendance:$company:$employee:$date")
            .execute()
    }

    override fun lockWindows(company: UUID, employee: UUID) {
        sql.query(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "capture-windows:$company:$employee",
            )
            .execute()
    }

    override fun findWindow(company: UUID, id: UUID): AttendanceCaptureWindowsRecord? =
        sql.selectFrom(W).where(W.COMPANY_ID.eq(company)).and(W.ID.eq(id)).fetchOne()

    override fun recentWindows(company: UUID, employee: UUID, since: OffsetDateTime): Int =
        sql.fetchCount(
            W,
            W.COMPANY_ID.eq(company).and(W.EMPLOYMENT_ID.eq(employee)).and(W.ISSUED_AT.ge(since)),
        )

    override fun insertWindow(row: AttendanceCaptureWindowsRecord) {
        sql.insertInto(W).set(row).execute()
    }

    override fun consumeWindow(company: UUID, id: UUID, eventId: UUID): Boolean =
        sql.update(W)
            .set(W.CONSUMED_BY, eventId)
            .where(W.COMPANY_ID.eq(company))
            .and(W.ID.eq(id))
            .and(W.CONSUMED_BY.isNull)
            .execute() == 1

    override fun find(company: UUID, id: UUID): AttendanceRow? =
        sql.select(E.asterisk(), R.asterisk())
            .from(E)
            .leftJoin(R)
            .on(R.COMPANY_ID.eq(E.COMPANY_ID).and(R.EVENT_ID.eq(E.ID)))
            .where(E.COMPANY_ID.eq(company))
            .and(E.ID.eq(id))
            .fetchOne {
                AttendanceRow(it.into(E), if (it[R.EVENT_ID] == null) null else it.into(R))
            }

    override fun entries(
        company: UUID,
        employee: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<AttendanceRow> =
        sql.select(E.asterisk(), R.asterisk())
            .from(E)
            .leftJoin(R)
            .on(R.COMPANY_ID.eq(E.COMPANY_ID).and(R.EVENT_ID.eq(E.ID)))
            .where(E.COMPANY_ID.eq(company))
            .and(E.EMPLOYMENT_ID.eq(employee))
            .and(E.WORK_DATE.between(from, until))
            .orderBy(E.RECEIVED_AT, E.ID)
            .limit(1024)
            .fetch { AttendanceRow(it.into(E), if (it[R.EVENT_ID] == null) null else it.into(R)) }

    override fun insert(row: AttendanceEventsRecord) {
        sql.insertInto(E).set(row).execute()
    }

    override fun insertReview(row: AttendanceReviewsRecord): UUID? =
        sql.insertInto(R)
            .set(row)
            .onConflict(R.COMPANY_ID, R.EVENT_ID)
            .doNothing()
            .returning(R.EVENT_ID)
            .fetchOne()
            ?.eventId
}
