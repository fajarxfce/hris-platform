package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.*

fun validateOvertimeInterval(value: OvertimeInterval, field: String = "interval"): Result<Unit> {
    if (
        value.startsAt.nano != 0 ||
            value.endsAt.nano != 0 ||
            value.startsAt.epochSecond % 60 != 0L ||
            value.endsAt.epochSecond % 60 != 0L ||
            value.endsAt <= value.startsAt
    )
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_overtime_interval",
                fields = mapOf(field to "whole_minutes_required"),
            )
        )
    val elapsed = Duration.between(value.startsAt, value.endsAt).toMinutes()
    if (elapsed !in 1..720 || value.breakMinutes !in 0..120 || value.breakMinutes >= elapsed)
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_overtime_interval",
                fields = mapOf(field to "duration_out_of_range"),
            )
        )
    return Result.Success(Unit)
}

fun validateOvertimePlan(
    date: LocalDate,
    window: OvertimeInterval,
    reason: String,
    expectedEmploymentVersion: Long,
): Result<Unit> {
    if (
        date.year !in 2000..2100 ||
            reason.isBlank() ||
            reason.length > 1000 ||
            expectedEmploymentVersion < 0
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_overtime_request"))
    return validateOvertimeInterval(window, "requested")
}

fun validateOvertimeSchedule(
    date: LocalDate,
    timezone: String,
    requested: OvertimeInterval,
    schedule: ScheduledDay,
): Result<Unit> {
    if (schedule.workDate != date || schedule is ScheduledDay.Unassigned)
        return Result.Failed(Failure(FailureKind.VALIDATION, "overtime_schedule_missing"))
    val zone = ZoneId.of(timezone)
    if (
        requested.startsAt < date.atStartOfDay(zone).toInstant() ||
            requested.endsAt > date.plusDays(2).atStartOfDay(zone).toInstant()
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "overtime_work_date_mismatch"))
    if (
        schedule is ScheduledDay.Work &&
            requested.startsAt < schedule.endsAt &&
            requested.endsAt > schedule.startsAt
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "overtime_overlaps_shift"))
    if (
        schedule is ScheduledDay.Off &&
            requested.startsAt >= date.plusDays(1).atStartOfDay(zone).toInstant()
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "overtime_work_date_mismatch"))
    return Result.Success(Unit)
}

fun validateOvertimeActual(
    request: OvertimeRequest,
    actual: OvertimeInterval,
    now: Instant,
): Result<Unit> {
    val valid = validateOvertimeInterval(actual, "actual")
    if (valid is Result.Failed) return valid
    if (
        actual.startsAt < request.requested.startsAt ||
            actual.endsAt > request.requested.endsAt ||
            actual.workedMinutes > request.requested.workedMinutes
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "overtime_outside_requested_window"))
    if (actual.endsAt > now)
        return Result.Failed(Failure(FailureKind.VALIDATION, "overtime_not_ended"))
    return Result.Success(Unit)
}
