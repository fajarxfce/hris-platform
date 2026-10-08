package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

fun validateShift(details: ShiftDetails): Result<Unit> {
    if (
        !details.code.matches(Regex("[A-Z0-9][A-Z0-9_-]{1,31}")) ||
            details.name.isBlank() ||
            details.name.length > 200 ||
            details.timezone !in ZoneId.getAvailableZoneIds()
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_shift"))
    val minutes = (Duration.between(details.startsAt, details.endsAt).toMinutes() + 1440) % 1440
    if (
        details.startsAt.second != 0 ||
            details.startsAt.nano != 0 ||
            details.endsAt.second != 0 ||
            details.endsAt.nano != 0 ||
            minutes == 0L ||
            details.breakMinutes < 0 ||
            details.breakMinutes >= minutes
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_shift_duration"))
    if (!details.maxAccuracyMeters.isFinite() || details.maxAccuracyMeters !in 1.0..10000.0)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_location_accuracy"))
    val fence = details.fence
    if (
        fence != null &&
            (!details.locationRequired ||
                !fence.latitude.isFinite() ||
                !fence.longitude.isFinite() ||
                !fence.radiusMeters.isFinite() ||
                fence.latitude !in -90.0..90.0 ||
                fence.longitude !in -180.0..180.0 ||
                fence.radiusMeters !in 1.0..100000.0)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_geofence"))
    return Result.Success(Unit)
}

fun scheduledWorkDay(
    date: LocalDate,
    shift: ShiftSnapshot,
    origin: CalendarOrigin,
    originVersion: Long,
): Result<ScheduledDay.Work> {
    val details = shift.details
    val zone = ZoneId.of(details.timezone)
    val localStart = date.atTime(details.startsAt)
    val localEnd =
        (if (details.endsAt > details.startsAt) date else date.plusDays(1)).atTime(details.endsAt)
    val startOffset = zone.rules.getValidOffsets(localStart).firstOrNull()
    val endOffset = zone.rules.getValidOffsets(localEnd).firstOrNull()
    if (startOffset == null || endOffset == null)
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "shift_timezone_gap",
                mapOf("workDate" to date.toString()),
            )
        )
    val start = localStart.toInstant(startOffset)
    val end = localEnd.toInstant(endOffset)
    val planned = Duration.between(start, end).toMinutes() - details.breakMinutes
    if (planned <= 0)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_shift_duration"))
    return Result.Success(
        ScheduledDay.Work(date, shift, start, end, planned, origin, originVersion)
    )
}
