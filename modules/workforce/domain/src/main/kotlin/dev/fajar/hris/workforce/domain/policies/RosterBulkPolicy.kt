package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.Employee
import dev.fajar.hris.people.domain.entities.EmploymentRevision
import dev.fajar.hris.workforce.domain.entities.*
import java.time.temporal.ChronoUnit

/** Validate and detach mutable caller collections before acquiring any resource. */
fun validateRosterBulkSelection(selection: RosterBulkSelection): Result<RosterBulkSelection> {
    if (
        selection.employeeIds.size !in 1..50 ||
            selection.dates.size !in 1..31 ||
            selection.employeeIds.size * selection.dates.size > 200 ||
            selection.employeeIds.toSet().size != selection.employeeIds.size ||
            selection.dates.toSet().size != selection.dates.size ||
            selection.dates.any { it.year !in 1900..9999 } ||
            (selection.mode == RosterBulkMode.SHIFT) != (selection.shift != null) ||
            (selection.shift?.version ?: 0) < 0
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_roster_bulk_selection"))
    val dates = selection.dates.sorted()
    if (ChronoUnit.DAYS.between(dates.first(), dates.last()) > 30)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_roster_bulk_selection"))
    return Result.Success(
        selection.copy(
            employeeIds = selection.employeeIds.sortedBy { it.toString() },
            dates = dates,
        )
    )
}

fun rosterBulkShift(
    selection: RosterBulkSelection,
    definition: ShiftDefinition?,
): Result<ShiftSnapshot?> {
    val reference = selection.shift ?: return Result.Success(null)
    if (definition == null || !definition.active || definition.id != reference.id)
        return Result.Failed(Failure(FailureKind.VALIDATION, "shift_unavailable"))
    if (definition.version != reference.version)
        return Result.Failed(Failure(FailureKind.CONFLICT, "shift_changed"))
    return Result.Success(ShiftSnapshot(definition.id, definition.version, definition.details))
}

/** Calculate eligibility and the two calendars without fetching data or changing storage. */
fun reviewRosterBulkEmployee(
    selection: RosterBulkSelection,
    employee: Employee,
    history: List<EmploymentRevision>,
    facts: CalendarFacts,
    shift: ShiftSnapshot?,
): Result<RosterBulkEmployee> {
    val rows = mutableListOf<RosterBulkDay>()
    for (date in selection.dates) {
        val terms =
            history
                .filter { it.cancellation == null && !it.terms.effectiveFrom.isAfter(date) }
                .maxWithOrNull(
                    compareBy<EmploymentRevision> { it.terms.effectiveFrom }.thenBy { it.revision }
                )
                ?.terms
        if (terms?.isWorkingOn(date) != true)
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "employee_unavailable",
                    parameters =
                        mapOf("employeeId" to employee.id.toString(), "workDate" to date.toString()),
                )
            )
        val current = facts.roster.singleOrNull { it.workDate == date }
        val changed =
            when (selection.mode) {
                RosterBulkMode.CALENDAR -> current?.active == true
                RosterBulkMode.SHIFT,
                RosterBulkMode.DAY_OFF -> current?.active != true || current.shift != shift
            }
        if (changed && current?.version == Long.MAX_VALUE)
            return Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
        val selectedFacts = facts.copy(from = date, until = date)
        val beforeResult = resolveCalendar(selectedFacts)
        if (beforeResult is Result.Failed) return beforeResult
        val before = (beforeResult as Result.Success).value.days.single()
        val afterResult =
            when {
                !changed -> Result.Success(before)
                selection.mode == RosterBulkMode.CALENDAR ->
                    resolveCalendar(selectedFacts.copy(roster = emptyList())).map {
                        it.days.single()
                    }
                shift != null ->
                    scheduledWorkDay(
                        date,
                        shift,
                        CalendarOrigin.ROSTER,
                        current?.version?.plus(1) ?: 0,
                    )
                else ->
                    Result.Success(
                        ScheduledDay.Off(
                            date,
                            CalendarOrigin.ROSTER,
                            current?.version?.plus(1) ?: 0,
                        )
                    )
            }
        if (afterResult is Result.Failed) return afterResult
        rows +=
            RosterBulkDay(
                date,
                current?.version,
                current?.active == true,
                before,
                (afterResult as Result.Success).value,
                changed,
            )
    }
    return Result.Success(
        RosterBulkEmployee(
            WorkforceEmployeeReference(
                employee.id,
                employee.employeeNumber,
                employee.person.legalName,
                employee.terms.status,
                employee.version,
            ),
            rows.toList(),
        )
    )
}
