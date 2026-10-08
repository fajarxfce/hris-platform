package dev.fajar.hris.workforce.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkPeriodPolicyTest {
    private val employee = UUID.randomUUID()
    private val month = YearMonth.of(2026, 9)
    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private val job = UUID.randomUUID()
    private val period =
        WorkPeriod(
            UUID.randomUUID(),
            month,
            WorkPeriodStatus.PROCESSING,
            "UTC",
            job,
            1,
            now,
            null,
            null,
        )
    private val calendar =
        EmployeeCalendar(
            null,
            (1..month.lengthOfMonth()).map { ScheduledDay.Unassigned(month.atDay(it)) },
        )

    private fun entry(
        kind: AttendanceKind,
        status: AttendanceStatus = AttendanceStatus.ACCEPTED,
        closingJob: UUID? = null,
        at: Instant = Instant.parse("2026-09-01T01:00:00Z"),
    ): AttendanceEntry {
        val capture =
            AttendanceCapture(
                UUID.randomUUID(),
                employee,
                month.atDay(1),
                kind,
                at,
                UUID.randomUUID(),
                null,
                true,
                null,
            )
        return AttendanceEntry(
            capture,
            UUID.randomUUID(),
            now,
            ScheduledDay.Unassigned(capture.workDate),
            AttendanceAssessment(status, emptySet(), closingJob),
            status,
            null,
            0,
        )
    }

    @Test
    fun malformedCalendarOrAnUnstartedPeriodCannotProduceSnapshots() {
        assertTrue(
            snapshotWorkPeriod(
                employee,
                period.copy(status = WorkPeriodStatus.OPEN, jobId = null),
                calendar,
                emptyList(),
                emptyList(),
            )
                is Result.Failed
        )
        assertTrue(
            snapshotWorkPeriod(
                employee,
                period,
                calendar.copy(days = List(30) { calendar.days.first() }),
                emptyList(),
                emptyList(),
            )
                is Result.Failed
        )
        assertTrue(
            snapshotWorkPeriod(
                employee,
                period,
                calendar.copy(days = calendar.days.drop(1)),
                emptyList(),
                emptyList(),
            )
                is Result.Failed
        )
    }

    @Test
    fun pendingEvidenceBelongsToItsAttemptAndIncompleteAcceptedPairsBlockClosing() {
        val original = entry(AttendanceKind.CLOCK_IN, AttendanceStatus.PENDING)
        val pending =
            snapshotWorkPeriod(employee, period, calendar, listOf(original), emptyList())
                as Result.Failed
        assertEquals("attendance_verification_required", pending.failure.code)
        val current = original.copy(initial = original.initial.copy(closingJobId = job))
        assertTrue(
            snapshotWorkPeriod(employee, period, calendar, listOf(current), emptyList())
                is Result.Success
        )
        val prior = original.copy(initial = original.initial.copy(closingJobId = UUID.randomUUID()))
        assertTrue(
            snapshotWorkPeriod(employee, period, calendar, listOf(prior), emptyList())
                is Result.Failed
        )
        val accepted = current.copy(status = AttendanceStatus.ACCEPTED)
        val incomplete =
            snapshotWorkPeriod(employee, period, calendar, listOf(accepted), emptyList())
                as Result.Failed
        assertEquals("attendance_incomplete", incomplete.failure.code)
    }

    @Test
    fun frozenEvidenceAndExplicitCorrectionsRemainDistinctFromMissingAttendance() {
        val date = month.atDay(1)
        val shift =
            ShiftSnapshot(
                UUID.randomUUID(),
                0,
                ShiftDetails(
                    "DAY",
                    "Day",
                    LocalTime.of(1, 0),
                    LocalTime.of(9, 0),
                    30,
                    "UTC",
                    WorkMode.REMOTE,
                    false,
                    100.0,
                    null,
                ),
            )
        val scheduled =
            (scheduledWorkDay(date, shift, CalendarOrigin.ROSTER, 0) as Result.Success).value
        val first = entry(AttendanceKind.CLOCK_IN).copy(schedule = scheduled)
        val last =
            entry(AttendanceKind.CLOCK_OUT, at = Instant.parse("2026-09-01T09:00:00Z"))
                .copy(schedule = scheduled)
        val correction =
            AttendanceCorrection(
                UUID.randomUUID(),
                employee,
                month.atDay(2),
                null,
                null,
                0,
                ScheduledDay.Unassigned(month.atDay(2)),
                UUID.randomUUID(),
                now,
                "Verified absence",
                0,
            )
        val snapshot =
            (snapshotWorkPeriod(employee, period, calendar, listOf(first, last), listOf(correction))
                    as Result.Success)
                .value
        assertEquals(450, snapshot.days[0].acceptedMinutes)
        assertEquals(WorkDayFact.WORKED, snapshot.days[0].fact)
        assertEquals(
            30,
            (snapshot.days[0].schedule as ScheduledDay.Work).shift.details.breakMinutes,
        )
        assertEquals(WorkDayFact.ABSENCE_RECORDED, snapshot.days[1].fact)
        assertEquals(correction.id, snapshot.days[1].correctionId)
        assertEquals(WorkDayFact.UNASSIGNED, snapshot.days[2].fact)
    }
}
