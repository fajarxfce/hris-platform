package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.workforce.data.models.AttendanceRow
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

interface AttendanceDataSource {
    fun lockDay(company: UUID, employee: UUID, date: LocalDate, shared: Boolean)

    fun lockWindows(company: UUID, employee: UUID)

    fun findWindow(company: UUID, id: UUID): AttendanceCaptureWindowsRecord?

    fun recentWindows(company: UUID, employee: UUID, since: OffsetDateTime): Int

    fun insertWindow(row: AttendanceCaptureWindowsRecord)

    fun consumeWindow(company: UUID, id: UUID, eventId: UUID): Boolean

    fun find(company: UUID, id: UUID): AttendanceRow?

    fun entries(
        company: UUID,
        employee: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<AttendanceRow>

    fun insert(row: AttendanceEventsRecord)

    fun insertReview(row: AttendanceReviewsRecord): UUID?
}
