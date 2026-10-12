package dev.fajar.hris.workforce.domain

import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.overtimePlanningRevision
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimePlanningRevisionTest {
    private val company = UUID.randomUUID()
    private val employee = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 1)
    private val off = ScheduledDay.Off(date, CalendarOrigin.HOLIDAY, 0, UUID.randomUUID())

    @Test
    fun equivalentFactsRetainTheirRevisionWhileScopeEmploymentAndCalendarChangesInvalidateIt() {
        val original = overtimePlanningRevision(company, employee, 3, "Asia/Jakarta", off)
        assertTrue(original.matches(Regex("[0-9a-f]{64}")))
        assertEquals(
            original,
            overtimePlanningRevision(company, employee, 3, "Asia/Jakarta", off.copy()),
        )
        for (changed in
            listOf(
                overtimePlanningRevision(UUID.randomUUID(), employee, 3, "Asia/Jakarta", off),
                overtimePlanningRevision(company, UUID.randomUUID(), 3, "Asia/Jakarta", off),
                overtimePlanningRevision(company, employee, 4, "Asia/Jakarta", off),
                overtimePlanningRevision(company, employee, 3, "Asia/Makassar", off),
                overtimePlanningRevision(
                    company,
                    employee,
                    3,
                    "Asia/Jakarta",
                    off.copy(workDate = date.plusDays(1)),
                ),
                overtimePlanningRevision(
                    company,
                    employee,
                    3,
                    "Asia/Jakarta",
                    off.copy(originVersion = 1),
                ),
                overtimePlanningRevision(
                    company,
                    employee,
                    3,
                    "Asia/Jakarta",
                    off.copy(holidayId = UUID.randomUUID()),
                ),
                overtimePlanningRevision(
                    company,
                    employee,
                    3,
                    "Asia/Jakarta",
                    off.copy(origin = CalendarOrigin.ROSTER, holidayId = null),
                ),
                overtimePlanningRevision(
                    company,
                    employee,
                    3,
                    "Asia/Jakarta",
                    ScheduledDay.Unassigned(date),
                ),
            )) assertNotEquals(original, changed)
    }

    @Test
    fun workRevisionRetainsTheExactShiftAndResolvedInterval() {
        val shift =
            ShiftSnapshot(
                UUID.randomUUID(),
                2,
                ShiftDetails(
                    "DAY",
                    "Day",
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    60,
                    "Asia/Jakarta",
                    WorkMode.REMOTE,
                    false,
                    100.0,
                    null,
                ),
            )
        val day =
            ScheduledDay.Work(
                date,
                shift,
                Instant.parse("2026-10-01T01:00:00Z"),
                Instant.parse("2026-10-01T10:00:00Z"),
                480,
                CalendarOrigin.PATTERN,
                4,
            )
        val original = overtimePlanningRevision(company, employee, 3, "Asia/Jakarta", day)
        assertEquals(
            original,
            overtimePlanningRevision(
                company,
                employee,
                3,
                "Asia/Jakarta",
                day.copy(shift = shift.copy()),
            ),
        )
        for (changed in
            listOf(
                day.copy(shift = shift.copy(id = UUID.randomUUID())),
                day.copy(shift = shift.copy(revision = 3)),
                day.copy(originVersion = 5),
                day.copy(origin = CalendarOrigin.ROSTER),
                day.copy(startsAt = day.startsAt.plusSeconds(60)),
                day.copy(endsAt = day.endsAt.plusSeconds(60)),
                day.copy(plannedMinutes = 450),
            )) assertNotEquals(
            original,
            overtimePlanningRevision(company, employee, 3, "Asia/Jakarta", changed),
        )
    }
}
