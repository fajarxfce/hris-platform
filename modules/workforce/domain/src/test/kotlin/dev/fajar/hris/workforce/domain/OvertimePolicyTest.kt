package dev.fajar.hris.workforce.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimePolicyTest {
    private val date = LocalDate.parse("2026-09-01")
    private val holiday = ScheduledDay.Off(date, CalendarOrigin.HOLIDAY, 0, UUID.randomUUID())
    private val window =
        OvertimeInterval(
            Instant.parse("2026-09-01T01:00:00Z"),
            Instant.parse("2026-09-01T04:00:00Z"),
            0,
        )
    private val request =
        OvertimeRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "E001",
            "Fictional employee",
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-09-01T00:00:00Z"),
            date,
            "Asia/Jakarta",
            holiday,
            window,
            "Inventory",
        )

    @Test
    fun minutePrecisionAndFiniteDurationsAreRequiredBeforeTimeArithmetic() {
        assertTrue(validateOvertimeInterval(window) is Result.Success)
        val invalid = window.copy(endsAt = window.startsAt)
        assertEquals(
            setOf("requested"),
            (validateOvertimePlan(date, invalid, "Inventory", 0) as Result.Failed)
                .failure
                .fields
                .keys,
        )
        assertEquals(
            setOf("actual"),
            (validateOvertimeActual(request, invalid, window.endsAt) as Result.Failed)
                .failure
                .fields
                .keys,
        )
        for (invalid in
            listOf(
                window.copy(startsAt = window.startsAt.plusNanos(1)),
                window.copy(endsAt = window.endsAt.plusSeconds(1)),
                window.copy(endsAt = window.startsAt),
                window.copy(endsAt = window.startsAt.plusSeconds(721 * 60)),
                window.copy(breakMinutes = 180),
                window.copy(breakMinutes = -1),
            )) assertEquals(
            "invalid_overtime_interval",
            (validateOvertimeInterval(invalid) as Result.Failed).failure.code,
        )
        assertTrue(
            validateOvertimeInterval(OvertimeInterval(Instant.MIN, Instant.MAX, 0)) is Result.Failed
        )
    }

    @Test
    fun actualTimeMustFitTheRequestedWindowAndBeCompleted() {
        val now = window.endsAt
        val actual =
            window.copy(
                startsAt = window.startsAt.plusSeconds(900),
                endsAt = window.endsAt.minusSeconds(900),
                breakMinutes = 15,
            )
        assertEquals(135, actual.workedMinutes)
        assertTrue(validateOvertimeActual(request, actual, now) is Result.Success)
        assertEquals(
            "overtime_not_ended",
            (validateOvertimeActual(request, actual, actual.endsAt.minusSeconds(1))
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "overtime_outside_requested_window",
            (validateOvertimeActual(
                    request,
                    window.copy(startsAt = window.startsAt.minusSeconds(60)),
                    now,
                )
                    as Result.Failed)
                .failure
                .code,
        )
        assertTrue(
            validateOvertimeActual(
                request.copy(requested = window.copy(breakMinutes = 60)),
                actual,
                now,
            )
                is Result.Failed
        )
    }

    @Test
    fun workDatesAreCompanyZonedAndNeverOverlapAnOvernightShift() {
        val shift =
            ShiftSnapshot(
                UUID.randomUUID(),
                0,
                ShiftDetails(
                    "NIGHT",
                    "Night",
                    LocalTime.of(22, 0),
                    LocalTime.of(6, 0),
                    60,
                    "Asia/Jakarta",
                    WorkMode.REMOTE,
                    false,
                    100.0,
                    null,
                ),
            )
        val schedule =
            (scheduledWorkDay(date, shift, CalendarOrigin.PATTERN, 0) as Result.Success).value
        val after =
            OvertimeInterval(
                Instant.parse("2026-09-01T23:00:00Z"),
                Instant.parse("2026-09-02T01:00:00Z"),
                0,
            )
        assertTrue(
            validateOvertimeSchedule(date, "Asia/Jakarta", after, schedule) is Result.Success
        )
        assertEquals(
            "overtime_overlaps_shift",
            (validateOvertimeSchedule(
                    date,
                    "Asia/Jakarta",
                    after.copy(startsAt = after.startsAt.minusSeconds(3600)),
                    schedule,
                )
                    as Result.Failed)
                .failure
                .code,
        )
        assertTrue(validateOvertimeSchedule(date, "Asia/Jakarta", after, holiday) is Result.Failed)
        assertTrue(
            validateOvertimeSchedule(date, "Asia/Jakarta", window, ScheduledDay.Unassigned(date))
                is Result.Failed
        )
    }

    @Test
    fun everyContributorAndCurrentBeneficiaryIsExcludedThroughDelegation() {
        val submitter = UUID.randomUUID()
        val linked = UUID.randomUUID()
        val independent = UUID.randomUUID()
        val submitted = request.copy(submittedBy = submitter)
        for (maker in
            listOf(
                request.authorId,
                requireNotNull(request.requesterAccountId),
                submitter,
                linked,
            )) {
            assertTrue(
                independentOvertimeDecision(submitted, maker, independent, linked) is Result.Failed
            )
            assertTrue(
                independentOvertimeDecision(submitted, independent, maker, linked) is Result.Failed
            )
        }
        assertTrue(
            independentOvertimeDecision(submitted, independent, independent, linked)
                is Result.Success
        )
    }

    @Test
    fun closingRetainsExactApprovedRequestsAndRejectsPartialOrForeignFacts() {
        val month = YearMonth.from(date)
        val period =
            WorkPeriod(
                UUID.randomUUID(),
                month,
                WorkPeriodStatus.PROCESSING,
                "Asia/Jakarta",
                UUID.randomUUID(),
                1,
                Instant.parse("2026-10-01T00:00:00Z"),
                null,
                null,
            )
        val calendar =
            EmployeeCalendar(null, (1..30).map { ScheduledDay.Unassigned(month.atDay(it)) })
        val approved =
            request.copy(
                status = OvertimeStatus.APPROVED,
                actual = window,
                approvedMinutes = 180,
                approvalId = UUID.randomUUID(),
                version = 2,
            )
        val snapshot =
            (snapshotWorkPeriod(
                    request.employeeId,
                    period,
                    calendar,
                    emptyList(),
                    emptyList(),
                    listOf(approved),
                )
                    as Result.Success)
                .value
        assertEquals(WorkDayFact.UNASSIGNED, snapshot.days.first().fact)
        assertEquals(holiday, snapshot.days.first().overtime.single().schedule)
        assertEquals(approved.id, snapshot.days.first().overtime.single().requestId)
        for (invalid in
            listOf(
                approved.copy(status = OvertimeStatus.PENDING),
                approved.copy(employeeId = UUID.randomUUID()),
                approved.copy(approvedMinutes = 200),
                approved.copy(actual = null),
            )) {
            assertEquals(
                "period_overtime_inconsistent",
                (snapshotWorkPeriod(
                        request.employeeId,
                        period,
                        calendar,
                        emptyList(),
                        emptyList(),
                        listOf(invalid),
                    )
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
        assertTrue(
            snapshotWorkPeriod(
                request.employeeId,
                period,
                calendar,
                emptyList(),
                emptyList(),
                listOf(approved, approved),
            )
                is Result.Failed
        )
    }
}
