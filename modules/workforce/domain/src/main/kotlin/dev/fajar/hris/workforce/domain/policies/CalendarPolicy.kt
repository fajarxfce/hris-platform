package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun effectiveScheduleAssignment(
    assignments: List<ScheduleAssignment>,
    date: LocalDate,
): ScheduleAssignment? =
    assignments
        .filter { !it.effectiveFrom.isAfter(date) }
        .maxWithOrNull(compareBy<ScheduleAssignment> { it.effectiveFrom }.thenBy { it.revision })

fun resolveCalendar(facts: CalendarFacts): Result<EmployeeCalendar> {
    val span = ChronoUnit.DAYS.between(facts.from, facts.until)
    if (span !in 0..61)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_calendar_range"))
    val days = mutableListOf<ScheduledDay>()
    val roster = facts.roster.filter { it.active }.associateBy { it.workDate }
    val holidays = facts.holidays.filter { it.active }.associateBy { it.workDate }
    for (offset in 0..span) {
        val date = facts.from.plusDays(offset)
        val rosterDay = roster[date]
        val holiday = holidays[date]
        val assignment = effectiveScheduleAssignment(facts.assignments, date)
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
    }
    return Result.Success(EmployeeCalendar(facts.scheduleVersion, days))
}
