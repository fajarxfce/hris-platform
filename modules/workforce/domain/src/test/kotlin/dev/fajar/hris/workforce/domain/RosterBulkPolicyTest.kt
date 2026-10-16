package dev.fajar.hris.workforce.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RosterBulkPolicyTest {
    private val date = LocalDate.parse("2026-10-02")
    private val employeeId = UUID.randomUUID()
    private val companyId = UUID.randomUUID()
    private val terms =
        EmploymentTerms(
            date.minusMonths(1),
            ContractKind.PERMANENT,
            date.minusMonths(1),
            null,
            EmploymentStatus.ACTIVE,
            null,
            null,
            null,
            null,
            null,
        )
    private val employee =
        Employee(
            employeeId,
            companyId,
            "E001",
            PersonProfile(UUID.randomUUID(), null, "Example employee", null, "ID", null),
            terms,
            null,
            0,
            0,
        )
    private val history =
        listOf(EmploymentRevision(0, terms, UUID.randomUUID(), "Fixture", Instant.EPOCH))
    private val shift =
        ShiftSnapshot(
            UUID.randomUUID(),
            2,
            ShiftDetails(
                "NIGHT",
                "Night",
                LocalTime.of(20, 0),
                LocalTime.of(4, 0),
                60,
                "Asia/Jakarta",
                WorkMode.REMOTE,
                false,
                50.0,
                null,
            ),
        )
    private val selection =
        RosterBulkSelection(listOf(employeeId), listOf(date), RosterBulkMode.DAY_OFF)
    private val facts =
        CalendarFacts(
            date,
            date,
            0,
            listOf(
                ScheduleAssignment(date.minusDays(1), 0, DayOfWeek.entries.associateWith { shift })
            ),
            emptyList(),
            emptyList(),
        )

    @Test
    fun selectionDetachesInputAndSortsBothDimensionsBeforeAnyWait() {
        val employees = mutableListOf(UUID.randomUUID(), employeeId)
        val dates = mutableListOf(date.plusDays(1), date)
        val result =
            (validateRosterBulkSelection(selection.copy(employeeIds = employees, dates = dates))
                    as Result.Success)
                .value
        val expectedEmployees = employees.sortedBy { it.toString() }
        employees.clear()
        dates.clear()
        assertEquals(expectedEmployees, result.employeeIds)
        assertEquals(listOf(date, date.plusDays(1)), result.dates)
    }

    @Test
    fun duplicateEmptyOversizedAndInconsistentSelectionsAreRejected() {
        val invalid =
            listOf(
                selection.copy(employeeIds = emptyList()),
                selection.copy(employeeIds = List(51) { UUID.randomUUID() }),
                selection.copy(employeeIds = listOf(employeeId, employeeId)),
                selection.copy(dates = emptyList()),
                selection.copy(dates = listOf(date, date)),
                selection.copy(dates = List(32) { date.plusDays(it.toLong()) }),
                selection.copy(dates = listOf(date, date.plusDays(31))),
                selection.copy(dates = listOf(LocalDate.of(1899, 1, 1))),
                selection.copy(dates = listOf(LocalDate.of(10000, 1, 1))),
                selection.copy(mode = RosterBulkMode.SHIFT),
                selection.copy(shift = ShiftReference(shift.id, 2)),
                selection.copy(mode = RosterBulkMode.SHIFT, shift = ShiftReference(shift.id, -1)),
                selection.copy(
                    employeeIds = List(50) { UUID.randomUUID() },
                    dates = List(5) { date.plusDays(it.toLong()) },
                ),
            )
        invalid.forEach {
            assertEquals(
                "invalid_roster_bulk_selection",
                (validateRosterBulkSelection(it) as? Result.Failed)?.failure?.code,
            )
        }
    }

    @Test
    fun maximumCapacityAndYearBoundaryRemainFinite() {
        val edge =
            selection.copy(
                employeeIds = List(50) { UUID.randomUUID() },
                dates = List(4) { LocalDate.of(9999, 12, 28).plusDays(it.toLong()) },
            )
        assertTrue(validateRosterBulkSelection(edge) is Result.Success)
        assertTrue(
            validateRosterBulkSelection(
                selection.copy(dates = listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1)))
            )
                is Result.Success
        )
    }

    @Test
    fun restoringCalendarDistinguishesNoOverrideFromAnExplicitDayOff() {
        val holiday = WorkHoliday(UUID.randomUUID(), date, "Holiday", true, 1)
        val loaded =
            facts.copy(roster = listOf(RosterOverride(date, null, 3)), holidays = listOf(holiday))
        val result =
            (reviewRosterBulkEmployee(
                    selection.copy(mode = RosterBulkMode.CALENDAR),
                    employee,
                    history,
                    loaded,
                    null,
                )
                    as Result.Success)
                .value
                .days
                .single()
        assertEquals(3L, result.expectedVersion)
        assertTrue(result.overrideActive)
        assertTrue(result.changed)
        assertEquals(CalendarOrigin.ROSTER, (result.before as ScheduledDay.Off).origin)
        assertEquals(CalendarOrigin.HOLIDAY, (result.after as ScheduledDay.Off).origin)
        assertEquals(holiday.id, result.after.holidayId)
        assertTrue(loaded.roster.single().active)
    }

    @Test
    fun reactivationUsesRetainedHeadAndOvernightStartingDate() {
        val selected =
            selection.copy(
                mode = RosterBulkMode.SHIFT,
                shift = ShiftReference(shift.id, shift.revision),
            )
        val loaded = facts.copy(roster = listOf(RosterOverride(date, null, 7, false)))
        val day =
            (reviewRosterBulkEmployee(selected, employee, history, loaded, shift) as Result.Success)
                .value
                .days
                .single()
        assertEquals(7L, day.expectedVersion)
        assertFalse(day.overrideActive)
        assertTrue(day.changed)
        val after = day.after as ScheduledDay.Work
        assertEquals(date, after.workDate)
        assertEquals(8, after.originVersion)
        assertEquals(Instant.parse("2026-10-02T13:00:00Z"), after.startsAt)
        assertEquals(Instant.parse("2026-10-02T21:00:00Z"), after.endsAt)
        assertEquals(420, after.plannedMinutes)
    }

    @Test
    fun identicalOverridesAndAlreadyRestoredDaysDoNotCreateAnotherRevision() {
        val identical = facts.copy(roster = listOf(RosterOverride(date, null, 4)))
        val same =
            (reviewRosterBulkEmployee(selection, employee, history, identical, null)
                    as Result.Success)
                .value
        assertEquals(same.days.single().before, same.days.single().after)
        assertFalse(same.days.single().changed)
        assertEquals(0, rosterBulkReview(companyId, selection, listOf(same)).changeCount)
        for (overrides in listOf(emptyList(), listOf(RosterOverride(date, null, 5, false)))) {
            val reset =
                (reviewRosterBulkEmployee(
                        selection.copy(mode = RosterBulkMode.CALENDAR),
                        employee,
                        history,
                        facts.copy(roster = overrides),
                        null,
                    )
                        as Result.Success)
                    .value
            assertFalse(reset.days.single().changed)
        }
        val explicit =
            (reviewRosterBulkEmployee(
                    selection,
                    employee,
                    history,
                    facts.copy(assignments = listOf(ScheduleAssignment(date, 0, emptyMap()))),
                    null,
                )
                    as Result.Success)
                .value
        assertTrue(explicit.days.single().changed)
    }

    @Test
    fun everySelectedDateMustBelongToWorkingEmployment() {
        val selected = selection.copy(dates = listOf(date, date.plusDays(1)))
        val ended =
            history +
                history
                    .single()
                    .copy(
                        revision = 1,
                        terms =
                            terms.copy(
                                effectiveFrom = date.plusDays(1),
                                status = EmploymentStatus.ENDED,
                            ),
                    )
        val failure =
            reviewRosterBulkEmployee(
                selected,
                employee,
                ended,
                facts.copy(until = date.plusDays(1)),
                null,
            )
                as Result.Failed
        assertEquals("employee_unavailable", failure.failure.code)
        assertEquals(date.plusDays(1).toString(), failure.failure.parameters["workDate"])
        assertEquals(employeeId.toString(), failure.failure.parameters["employeeId"])
    }

    @Test
    fun aChangedRevisionCannotWrapAroundAndLookLikeANewRoster() {
        val result =
            reviewRosterBulkEmployee(
                selection,
                employee,
                history,
                facts.copy(roster = listOf(RosterOverride(date, shift, Long.MAX_VALUE))),
                null,
            )
        assertEquals("stale_version", (result as? Result.Failed)?.failure?.code)
    }

    @Test
    fun restoredDstGapsAreRejectedBeforeAnyWrite() {
        val dst = LocalDate.of(2026, 3, 8)
        val missing =
            shift.copy(
                details =
                    shift.details.copy(
                        startsAt = LocalTime.of(2, 30),
                        endsAt = LocalTime.of(10, 30),
                        timezone = "America/New_York",
                    )
            )
        val employment =
            history.map {
                it.copy(
                    terms =
                        terms.copy(effectiveFrom = dst.minusDays(1), startDate = dst.minusDays(1))
                )
            }
        val loaded =
            CalendarFacts(
                dst,
                dst,
                0,
                listOf(ScheduleAssignment(dst, 0, mapOf(DayOfWeek.SUNDAY to missing))),
                listOf(RosterOverride(dst, null, 0)),
                emptyList(),
            )
        val result =
            reviewRosterBulkEmployee(
                selection.copy(dates = listOf(dst), mode = RosterBulkMode.CALENDAR),
                employee,
                employment,
                loaded,
                null,
            )
        assertEquals("shift_timezone_gap", (result as? Result.Failed)?.failure?.code)
    }

    @Test
    fun reviewRevisionBindsCompanyEmploymentObservedHeadsAndCalendarContent() {
        val row =
            (reviewRosterBulkEmployee(selection, employee, history, facts, null) as Result.Success)
                .value
        val original = rosterBulkReview(companyId, selection, listOf(row))
        assertEquals(original, rosterBulkReview(companyId, selection, listOf(row.copy())))
        val variants =
            listOf(
                rosterBulkReview(UUID.randomUUID(), selection, listOf(row)),
                rosterBulkReview(
                    companyId,
                    selection,
                    listOf(row.copy(employee = row.employee.copy(version = 1))),
                ),
                rosterBulkReview(
                    companyId,
                    selection,
                    listOf(
                        row.copy(
                            days =
                                row.days.map {
                                    it.copy(expectedVersion = 0, overrideActive = false)
                                }
                        )
                    ),
                ),
                rosterBulkReview(
                    companyId,
                    selection,
                    listOf(
                        row.copy(
                            days = row.days.map { it.copy(before = ScheduledDay.Unassigned(date)) }
                        )
                    ),
                ),
                rosterBulkReview(
                    companyId,
                    selection.copy(mode = RosterBulkMode.CALENDAR),
                    listOf(row),
                ),
            )
        assertTrue(original.revision.matches(Regex("[0-9a-f]{64}")))
        variants.forEach { assertNotEquals(original.revision, it.revision) }
    }

    @Test
    fun selectedShiftMustBeActiveAndKeepTheReviewedRevision() {
        val selected =
            selection.copy(
                mode = RosterBulkMode.SHIFT,
                shift = ShiftReference(shift.id, shift.revision),
            )
        val definition = ShiftDefinition(shift.id, shift.details, true, shift.revision)
        assertEquals(shift, (rosterBulkShift(selected, definition) as Result.Success).value)
        assertEquals(
            "shift_changed",
            (rosterBulkShift(selected, definition.copy(version = 3)) as? Result.Failed)
                ?.failure
                ?.code,
        )
        for (missing in listOf(null, definition.copy(active = false))) {
            assertEquals(
                "shift_unavailable",
                (rosterBulkShift(selected, missing) as? Result.Failed)?.failure?.code,
            )
        }
    }
}
