package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.people.domain.entities.EmploymentRevision
import dev.fajar.hris.workforce.domain.entities.*
import java.time.temporal.ChronoUnit

fun validateRequestedLeaveDays(days: List<RequestedLeaveDay>, reason: String): Result<Unit> {
    if (
        days.size !in 1..366 ||
            days.map { it.workDate }.toSet().size != days.size ||
            days.any { it.workDate.year !in 1900..2200 } ||
            reason.isBlank() ||
            reason.length > 1000
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_request"))
    if (ChronoUnit.DAYS.between(days.minOf { it.workDate }, days.maxOf { it.workDate }) > 365)
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_range_too_large"))
    return Result.Success(Unit)
}

fun planLeaveDays(
    requested: List<RequestedLeaveDay>,
    calendar: EmployeeCalendar,
    history: List<EmploymentRevision>,
    policy: LeavePolicy,
): Result<List<LeaveDay>> {
    val schedule = calendar.days.associateBy { it.workDate }
    val days = mutableListOf<LeaveDay>()
    for (input in requested.sortedBy { it.workDate }) {
        val day = schedule[input.workDate]
        if (day == null || day is ScheduledDay.Unassigned)
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "leave_schedule_missing",
                    mapOf("workDate" to input.workDate.toString()),
                )
            )
        if (day is ScheduledDay.Off) continue
        day as ScheduledDay.Work
        val terms =
            history
                .filter { !it.terms.effectiveFrom.isAfter(input.workDate) }
                .maxWithOrNull(
                    compareBy<EmploymentRevision> { it.terms.effectiveFrom }.thenBy { it.revision }
                )
                ?.terms
        if (
            terms == null ||
                !terms.isWorkingOn(input.workDate) ||
                terms.contract !in policy.allowedContracts ||
                input.workDate.isBefore(
                    terms.startDate.plusMonths(policy.minServiceMonths.toLong())
                )
        )
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "leave_employee_ineligible",
                    mapOf("workDate" to input.workDate.toString()),
                )
            )
        if (input.portion != LeavePortion.FULL && !policy.allowPartialDays)
            return Result.Failed(Failure(FailureKind.VALIDATION, "partial_leave_unavailable"))
        val minutes =
            when (input.portion) {
                LeavePortion.FULL -> day.plannedMinutes
                LeavePortion.FIRST_HALF -> (day.plannedMinutes + 1) / 2
                LeavePortion.SECOND_HALF -> day.plannedMinutes / 2
            }
        if (minutes <= 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "leave_duration_too_short"))
        days +=
            LeaveDay(
                input.workDate,
                input.portion,
                day.startsAt,
                day.endsAt,
                day.plannedMinutes,
                minutes,
                day.shift.id,
                day.shift.revision,
                day.origin,
                day.originVersion,
            )
    }
    if (days.isEmpty())
        return Result.Failed(Failure(FailureKind.VALIDATION, "no_leave_working_days"))
    if (days.sumOf { it.portion.halfDays } > policy.maxRequestDays * 2)
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_request_limit"))
    return Result.Success(days.toList())
}

fun validateLeaveOverlap(days: List<LeaveDay>, occupied: List<LeaveOccupancy>): Result<Unit> {
    val byDate = occupied.associateBy { it.workDate }
    return if (days.any { (it.portion.mask and (byDate[it.workDate]?.mask ?: 0)) != 0 })
        Result.Failed(Failure(FailureKind.CONFLICT, "leave_overlap"))
    else Result.Success(Unit)
}
