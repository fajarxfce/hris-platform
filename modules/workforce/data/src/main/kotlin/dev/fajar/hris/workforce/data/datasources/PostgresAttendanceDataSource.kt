package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.AttendanceCaptureWindows.ATTENDANCE_CAPTURE_WINDOWS as W
import dev.fajar.hris.schema.tables.AttendanceEvents.ATTENDANCE_EVENTS as E
import dev.fajar.hris.schema.tables.AttendanceReviews.ATTENDANCE_REVIEWS as R
import dev.fajar.hris.schema.tables.AttendanceSyncHeads.ATTENDANCE_SYNC_HEADS as H
import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.workforce.data.models.AttendanceExceptionRow
import dev.fajar.hris.workforce.data.models.AttendanceRow
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresAttendanceDataSource(private val sql: DSLContext) : AttendanceDataSource {
    override fun dayHead(company: UUID, id: UUID): AttendanceSyncHeadsRecord? =
        sql.selectFrom(H).where(H.COMPANY_ID.eq(company), H.ID.eq(id)).fetchOne()

    override fun exceptions(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        kind: String?,
        afterDate: LocalDate?,
        afterEmployee: UUID?,
        limit: Int,
    ): List<AttendanceExceptionRow> =
        sql.resultQuery(
                """
                WITH day_counts AS (
                    SELECT e.company_id, e.employment_id, e.work_date,
                        count(*) FILTER (WHERE e.initial_status='PENDING' AND r.event_id IS NULL)::int AS pending_count,
                        count(*) FILTER (WHERE e.kind='CLOCK_IN' AND coalesce(r.decision='ACCEPT', e.initial_status='ACCEPTED'))::int AS accepted_in,
                        count(*) FILTER (WHERE e.kind='CLOCK_OUT' AND coalesce(r.decision='ACCEPT', e.initial_status='ACCEPTED'))::int AS accepted_out,
                        max(e.received_at) AS last_received_at
                    FROM attendance_events e
                    LEFT JOIN attendance_reviews r ON r.company_id=e.company_id AND r.event_id=e.id
                    WHERE e.company_id=? AND e.work_date BETWEEN ? AND ?
                        AND (?::date IS NULL OR e.work_date < ?::date OR
                            (e.work_date=?::date AND e.employment_id > ?::uuid))
                    GROUP BY e.company_id, e.employment_id, e.work_date
                ), selected AS (
                    SELECT d.*, c.revision AS correction_version
                    FROM day_counts d
                    LEFT JOIN LATERAL (
                        SELECT revision FROM attendance_corrections
                        WHERE company_id=d.company_id AND employment_id=d.employment_id AND work_date=d.work_date
                        ORDER BY revision DESC LIMIT 1
                    ) c ON true
                    WHERE ((?::text IS NULL OR ?::text='PENDING') AND d.pending_count > 0)
                        OR ((?::text IS NULL OR ?::text='INCOMPLETE') AND c.revision IS NULL AND
                            ((d.accepted_in=1) <> (d.accepted_out=1)))
                    ORDER BY d.work_date DESC, d.employment_id
                    LIMIT ?
                )
                SELECT s.employment_id, e.employee_number, p.legal_name, s.work_date,
                    s.pending_count, s.accepted_in, s.accepted_out, s.correction_version, s.last_received_at
                FROM selected s
                JOIN employments e ON e.company_id=s.company_id AND e.id=s.employment_id
                JOIN persons p ON p.id=e.person_id
                ORDER BY s.work_date DESC, s.employment_id
                """
                    .trimIndent(),
                company,
                from,
                until,
                afterDate,
                afterDate,
                afterDate,
                afterEmployee,
                kind,
                kind,
                kind,
                kind,
                limit,
            )
            .fetch { row ->
                AttendanceExceptionRow(
                    requireNotNull(row.get("employment_id", UUID::class.java)),
                    requireNotNull(row.get("employee_number", String::class.java)),
                    requireNotNull(row.get("legal_name", String::class.java)),
                    requireNotNull(row.get("work_date", LocalDate::class.java)),
                    requireNotNull(row.get("pending_count", Int::class.java)),
                    requireNotNull(row.get("accepted_in", Int::class.java)),
                    requireNotNull(row.get("accepted_out", Int::class.java)),
                    row.get("correction_version", Long::class.javaObjectType),
                    requireNotNull(row.get("last_received_at", OffsetDateTime::class.java)),
                )
            }

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
