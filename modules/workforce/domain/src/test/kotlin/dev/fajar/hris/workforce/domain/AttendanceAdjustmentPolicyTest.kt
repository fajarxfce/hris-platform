package dev.fajar.hris.workforce.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.domain.entities.EmploymentStatus
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AttendanceAdjustmentPolicyTest {
    private val employee = UUID.randomUUID()
    private val month = YearMonth.of(2026, 9)
    private val date = month.atDay(30)
    private val now = Instant.parse("2026-10-01T03:00:00Z")
    private val days =
        (1..month.lengthOfMonth()).map {
            ClosedWorkDay(
                month.atDay(it),
                WorkDayFact.UNASSIGNED,
                0,
                ScheduledDay.Unassigned(month.atDay(it)),
                emptyList(),
                null,
            )
        }
    private val source = WorkPeriodSnapshot(employee, month, days)
    private val correction =
        AttendanceCorrection(
            UUID.randomUUID(),
            employee,
            date,
            Instant.parse("2026-09-30T01:00:00Z"),
            Instant.parse("2026-09-30T09:00:00Z"),
            60,
            days.last().schedule,
            UUID.randomUUID(),
            now,
            "Reviewed late attendance",
            0,
        )

    private fun entry(status: AttendanceStatus = AttendanceStatus.ACCEPTED): AttendanceEntry {
        val capture =
            AttendanceCapture(
                UUID.randomUUID(),
                employee,
                date,
                AttendanceKind.CLOCK_IN,
                requireNotNull(correction.clockIn),
                UUID.randomUUID(),
                null,
                true,
                null,
            )
        return AttendanceEntry(
            capture,
            UUID.randomUUID(),
            now,
            days.last().schedule,
            AttendanceAssessment(
                AttendanceStatus.PENDING,
                setOf(AttendanceIssue.PERIOD_LOCKED),
                UUID.randomUUID(),
            ),
            status,
            null,
            0,
        )
    }

    @Test
    fun correctionReplacesOneDayAndRetainsFrozenScheduleOvertimeAndOtherDays() {
        val overtime =
            ApprovedOvertime(
                UUID.randomUUID(),
                3,
                OvertimeInterval(
                    Instant.parse("2026-09-30T10:00:00Z"),
                    Instant.parse("2026-09-30T12:00:00Z"),
                    0,
                ),
                120,
                days.last().schedule,
            )
        val original =
            source.copy(days = days.dropLast(1) + days.last().copy(overtime = listOf(overtime)))
        val raw = entry()
        val updated =
            (adjustClosedWorkSnapshot(original, correction, listOf(raw)) as Result.Success).value
        assertEquals(original.days.dropLast(1), updated.days.dropLast(1))
        val day = updated.days.last()
        assertEquals(WorkDayFact.WORKED, day.fact)
        assertEquals(420, day.acceptedMinutes)
        assertEquals(correction.id, day.correctionId)
        assertEquals(listOf(raw.capture.id), day.evidenceIds)
        assertEquals(original.days.last().schedule, day.schedule)
        assertEquals(listOf(overtime), day.overtime)
        assertEquals(WorkDayFact.UNASSIGNED, original.days.last().fact)
        assertEquals(AttendanceStatus.PENDING, raw.initial.status)
        assertThrows(UnsupportedOperationException::class.java) {
            (updated.days as MutableList).clear()
        }
    }

    @Test
    fun absenceRemainsExplicitAndIsNotReclassifiedAsMissingAttendance() {
        val updated =
            (adjustClosedWorkSnapshot(
                    source,
                    correction.copy(clockIn = null, clockOut = null, breakMinutes = 0),
                    emptyList(),
                )
                    as Result.Success)
                .value
        assertEquals(WorkDayFact.ABSENCE_RECORDED, updated.days.last().fact)
        assertEquals(0, updated.days.last().acceptedMinutes)
        assertEquals(WorkDayFact.UNASSIGNED, updated.days.first().fact)
    }

    @Test
    fun differentEmployeeMissingDateChangedScheduleAndUnresolvedEvidenceCannotProduceARevision() {
        val failures =
            listOf(
                adjustClosedWorkSnapshot(
                    source.copy(employeeId = UUID.randomUUID()),
                    correction,
                    emptyList(),
                ),
                adjustClosedWorkSnapshot(
                    source.copy(days = days.dropLast(1)),
                    correction,
                    emptyList(),
                ),
                adjustClosedWorkSnapshot(
                    source.copy(days = List(30) { days.first() }),
                    correction,
                    emptyList(),
                ),
                adjustClosedWorkSnapshot(
                    source,
                    correction.copy(schedule = ScheduledDay.Unassigned(month.atDay(1))),
                    emptyList(),
                ),
                adjustClosedWorkSnapshot(
                    source,
                    correction,
                    listOf(entry(AttendanceStatus.PENDING)),
                ),
                adjustClosedWorkSnapshot(
                    source,
                    correction,
                    listOf(
                        entry().let {
                            it.copy(capture = it.capture.copy(employeeId = UUID.randomUUID()))
                        }
                    ),
                ),
                adjustClosedWorkSnapshot(
                    source,
                    correction,
                    listOf(
                        entry().let {
                            it.copy(capture = it.capture.copy(workDate = month.atDay(1)))
                        }
                    ),
                ),
                adjustClosedWorkSnapshot(source, correction, List(33) { entry() }),
            )
        failures.forEach {
            assertEquals("attendance_adjustment_source_invalid", (it as Result.Failed).failure.code)
        }
    }

    @Test
    fun aRevisionCannotDropOriginalCapturedEvidence() {
        val raw = entry()
        val original =
            source.copy(
                days = days.dropLast(1) + days.last().copy(evidenceIds = listOf(raw.capture.id))
            )
        assertTrue(adjustClosedWorkSnapshot(original, correction, emptyList()) is Result.Failed)
        assertTrue(adjustClosedWorkSnapshot(original, correction, listOf(raw)) is Result.Success)
        assertTrue(
            adjustClosedWorkSnapshot(original, correction, listOf(raw, raw)) is Result.Failed
        )
    }

    @Test
    fun everyPendingCaptureRequiresExactlyOneObservedDecision() {
        val pending = entry(AttendanceStatus.PENDING)
        val reviewed = entry(AttendanceStatus.REJECTED)
        val decision =
            AttendanceEvidenceDecision(pending.capture.id, 0, AttendanceReviewDecision.ACCEPT)
        assertTrue(
            validateAdjustmentEvidence(listOf(pending, reviewed), listOf(decision))
                is Result.Success
        )
        for (decisions in
            listOf(
                emptyList(),
                listOf(decision.copy(expectedVersion = 1)),
                listOf(decision.copy(eventId = reviewed.capture.id)),
            )) assertEquals(
            "attendance_adjustment_evidence_changed",
            (validateAdjustmentEvidence(listOf(pending, reviewed), decisions) as Result.Failed)
                .failure
                .code,
        )
        assertTrue(
            validateAdjustmentEvidence(listOf(pending), listOf(decision, decision)) is Result.Failed
        )
        assertTrue(
            validateAdjustmentEvidence(
                listOf(pending),
                listOf(decision.copy(expectedVersion = Long.MAX_VALUE)),
            )
                is Result.Failed
        )
        assertTrue(
            validateAdjustmentEvidence(listOf(pending, pending), listOf(decision)) is Result.Failed
        )
        assertTrue(validateAdjustmentEvidence(List(33) { entry() }, emptyList()) is Result.Failed)
    }

    @Test
    fun reviewComparisonIsOrderIndependentAndBindsEveryMutableSourceAndOwner() {
        val company = UUID.randomUUID()
        val reference =
            WorkforceEmployeeReference(
                employee,
                "E001",
                "Example Employee",
                EmploymentStatus.ACTIVE,
                0,
            )
        val period =
            WorkPeriod(
                UUID.randomUUID(),
                month,
                WorkPeriodStatus.CLOSED,
                "UTC",
                UUID.randomUUID(),
                2,
                now,
                now,
                null,
            )
        val a = entry()
        val b = entry()
        val expected =
            attendanceAdjustmentRevision(company, reference, period, date, 0, null, listOf(a, b))
        assertEquals(
            expected,
            attendanceAdjustmentRevision(company, reference, period, date, 0, null, listOf(b, a)),
        )
        val changed =
            listOf(
                attendanceAdjustmentRevision(
                    UUID.randomUUID(),
                    reference,
                    period,
                    date,
                    0,
                    null,
                    listOf(a, b),
                ),
                attendanceAdjustmentRevision(
                    company,
                    reference.copy(version = 1),
                    period,
                    date,
                    0,
                    null,
                    listOf(a, b),
                ),
                attendanceAdjustmentRevision(
                    company,
                    reference,
                    period.copy(jobId = UUID.randomUUID()),
                    date,
                    0,
                    null,
                    listOf(a, b),
                ),
                attendanceAdjustmentRevision(
                    company,
                    reference,
                    period,
                    date.minusDays(1),
                    0,
                    null,
                    listOf(a, b),
                ),
                attendanceAdjustmentRevision(
                    company,
                    reference,
                    period,
                    date,
                    1,
                    null,
                    listOf(a, b),
                ),
                attendanceAdjustmentRevision(
                    company,
                    reference,
                    period,
                    date,
                    0,
                    correction,
                    listOf(a, b),
                ),
                attendanceAdjustmentRevision(
                    company,
                    reference,
                    period,
                    date,
                    0,
                    null,
                    listOf(a.copy(version = 1), b),
                ),
                attendanceAdjustmentRevision(company, reference, period, date, 0, null, listOf(a)),
            )
        changed.forEach { assertNotEquals(expected, it) }
    }

    @Test
    fun extremeCaptureInstantsRemainValidationFailuresWithoutDateConversionErrors() {
        assertEquals(
            "invalid_attendance_correction",
            (validateAttendanceCorrection(
                    date,
                    Instant.MIN,
                    Instant.MIN.plusSeconds(3600),
                    0,
                    "Reviewed late attendance",
                    now,
                )
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "invalid_attendance_correction",
            (validateAttendanceCorrection(
                    date,
                    Instant.MAX.minusSeconds(3600),
                    Instant.MAX,
                    0,
                    "Reviewed late attendance",
                    now,
                )
                    as Result.Failed)
                .failure
                .code,
        )
        assertTrue(
            validateAttendanceCorrection(
                date,
                correction.clockIn,
                correction.clockOut,
                60,
                "Reviewed late attendance",
                now,
            )
                is Result.Success
        )
    }

    @Test
    fun interruptionPropagatesInsteadOfBecomingADataFailure() {
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                adjustClosedWorkSnapshot(source, correction, emptyList())
            }
        } finally {
            Thread.interrupted()
        }
    }
}
