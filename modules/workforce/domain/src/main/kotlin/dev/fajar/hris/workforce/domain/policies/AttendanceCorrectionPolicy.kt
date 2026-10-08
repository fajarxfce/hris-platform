package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.math.abs

fun validateAttendanceCorrection(
    date: LocalDate,
    clockIn: Instant?,
    clockOut: Instant?,
    breakMinutes: Int,
    reason: String,
    at: Instant,
): Result<Unit> {
    if (
        reason.isBlank() ||
            reason.length > 1000 ||
            breakMinutes < 0 ||
            ((clockIn == null) != (clockOut == null))
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_attendance_correction"))
    if (clockIn == null || clockOut == null)
        return if (breakMinutes == 0) Result.Success(Unit)
        else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_attendance_correction"))
    val duration = Duration.between(clockIn, clockOut)
    if (
        duration.isNegative ||
            duration.isZero ||
            duration > Duration.ofHours(24) ||
            breakMinutes >= duration.toMinutes() ||
            clockIn != clockIn.truncatedTo(ChronoUnit.MINUTES) ||
            clockOut != clockOut.truncatedTo(ChronoUnit.MINUTES) ||
            clockOut.isAfter(at.plusSeconds(30)) ||
            abs(ChronoUnit.DAYS.between(date, clockIn.atOffset(ZoneOffset.UTC).toLocalDate())) > 2
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_attendance_correction"))
    return Result.Success(Unit)
}
