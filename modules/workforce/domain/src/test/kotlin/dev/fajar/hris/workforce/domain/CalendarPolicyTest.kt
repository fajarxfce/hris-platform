package dev.fajar.hris.workforce.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CalendarPolicyTest {
    private fun shift(
        start: String = "22:00",
        end: String = "06:00",
        zone: String = "Asia/Jakarta",
        breakMinutes: Int = 30,
    ) =
        ShiftSnapshot(
            UUID.randomUUID(),
            0,
            ShiftDetails(
                "NIGHT",
                "Night",
                LocalTime.parse(start),
                LocalTime.parse(end),
                breakMinutes,
                zone,
                WorkMode.REMOTE,
                false,
                50.0,
                null,
            ),
        )

    @Test
    fun overnightShiftRetainsItsWorkDate() {
        val date = LocalDate.parse("2026-10-08")
        val day =
            (scheduledWorkDay(date, shift(), CalendarOrigin.PATTERN, 0) as Result.Success).value
        assertEquals(date, day.workDate)
        assertEquals(Instant.parse("2026-10-08T15:00:00Z"), day.startsAt)
        assertEquals(Instant.parse("2026-10-08T23:00:00Z"), day.endsAt)
        assertEquals(450, day.plannedMinutes)
    }

    @Test
    fun nonexistentDstTimeRequiresAnExplicitRosterCorrection() {
        val result =
            scheduledWorkDay(
                LocalDate.parse("2026-03-08"),
                shift("02:30", "10:30", "America/New_York"),
                CalendarOrigin.PATTERN,
                0,
            )
        assertEquals("shift_timezone_gap", (result as Result.Failed).failure.code)
    }

    @Test
    fun repeatedDstTimeUsesTheEarlierOffsetAndRealElapsedMinutes() {
        val result =
            scheduledWorkDay(
                LocalDate.parse("2026-11-01"),
                shift("01:30", "02:30", "America/New_York", 0),
                CalendarOrigin.ROSTER,
                0,
            )
        val day = (result as Result.Success).value
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), day.startsAt)
        assertEquals(Instant.parse("2026-11-01T07:30:00Z"), day.endsAt)
        assertEquals(120, day.plannedMinutes)
    }

    @Test
    fun explicitRosterAndHolidayPrecedenceIsDeterministic() {
        val date = LocalDate.parse("2026-10-08")
        val shift = shift()
        val assignment = ScheduleAssignment(date, 0, mapOf(date.dayOfWeek to shift))
        val holiday = WorkHoliday(UUID.randomUUID(), date, "Company holiday", true, 0)
        val facts = CalendarFacts(date, date, 0, listOf(assignment), emptyList(), listOf(holiday))
        assertTrue(
            (resolveCalendar(facts) as Result.Success).value.days.single() is ScheduledDay.Off
        )
        val forced =
            (resolveCalendar(facts.copy(roster = listOf(RosterOverride(date, shift, 0))))
                    as Result.Success)
                .value
                .days
                .single()
        assertTrue(forced is ScheduledDay.Work)
        val off =
            (resolveCalendar(facts.copy(roster = listOf(RosterOverride(date, null, 1))))
                    as Result.Success)
                .value
                .days
                .single() as ScheduledDay.Off
        assertEquals(CalendarOrigin.ROSTER, off.origin)
        assertEquals(1, off.originVersion)
    }

    @Test
    fun futureScheduleRevisionsDoNotChangeEarlierDates() {
        val first = LocalDate.parse("2026-10-08")
        val second = first.plusDays(1)
        val old = ScheduleAssignment(first, 0, DayOfWeek.entries.associateWith { shift() })
        val next = ScheduleAssignment(second, 1, emptyMap())
        val calendar =
            (resolveCalendar(
                    CalendarFacts(first, second, 1, listOf(old, next), emptyList(), emptyList())
                )
                    as Result.Success)
                .value
        assertTrue(calendar.days.first() is ScheduledDay.Work)
        assertTrue(calendar.days.last() is ScheduledDay.Off)
        assertEquals(1, calendar.scheduleVersion)
    }

    @Test
    fun invalidLocationAndZeroLengthShiftsAreRejected() {
        assertTrue(validateShift(shift("08:00", "08:00").details) is Result.Failed)
        assertTrue(
            validateShift(
                shift().details.copy(locationRequired = true, fence = GeoFence(91.0, 0.0, 50.0))
            )
                is Result.Failed
        )
        assertTrue(
            validateShift(shift().details.copy(maxAccuracyMeters = Double.NaN)) is Result.Failed
        )
    }
}
