package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*

fun resolveCalendar(facts: CalendarFacts): Result<EmployeeCalendar> {
    val days = mutableListOf<ScheduledDay>()
    val roster = facts.roster.associateBy { it.workDate }
    val holidays = facts.holidays.filter { it.active }.associateBy { it.workDate }
    var date = facts.from
    while (!date.isAfter(facts.until)) {
        val rosterDay = roster[date]
        val holiday = holidays[date]
        val assignment =
            facts.assignments
                .filter { !it.effectiveFrom.isAfter(date) }
                .maxWithOrNull(
                    compareBy<ScheduleAssignment> { it.effectiveFrom }.thenBy { it.revision }
                )
        val resolved: Result<ScheduledDay> =
            when {
                rosterDay != null ->
                    rosterDay.shift?.let {
                        scheduledWorkDay(date, it, CalendarOrigin.ROSTER, rosterDay.version)
                    }
                        ?: Result.Success(
                            ScheduledDay.Off(date, CalendarOrigin.ROSTER, rosterDay.version)
                        )
                holiday != null ->
                    Result.Success(
                        ScheduledDay.Off(date, CalendarOrigin.HOLIDAY, holiday.version, holiday.id)
                    )
                assignment == null -> Result.Success(ScheduledDay.Unassigned(date))
                else ->
                    assignment.days[date.dayOfWeek]?.let {
                        scheduledWorkDay(date, it, CalendarOrigin.PATTERN, assignment.revision)
                    }
                        ?: Result.Success(
                            ScheduledDay.Off(date, CalendarOrigin.PATTERN, assignment.revision)
                        )
            }
        if (resolved is Result.Failed) return resolved
        days += (resolved as Result.Success).value
        date = date.plusDays(1)
    }
    return Result.Success(EmployeeCalendar(facts.scheduleVersion, days))
}
