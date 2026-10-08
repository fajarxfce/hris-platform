package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.Duration
import java.time.LocalDate

fun validateAttendanceSequence(
    capture: AttendanceCapture,
    entries: List<AttendanceEntry>,
): Result<Unit> {
    val accepted = entries.filter { it.status == AttendanceStatus.ACCEPTED }
    if (accepted.any { it.capture.kind == capture.kind })
        return Result.Failed(Failure(FailureKind.CONFLICT, "attendance_sequence_conflict"))
    if (capture.kind == AttendanceKind.CLOCK_OUT) {
        val start =
            accepted
                .singleOrNull { it.capture.kind == AttendanceKind.CLOCK_IN }
                ?.capture
                ?.capturedAt
                ?: return Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_sequence_conflict")
                )
        val duration = Duration.between(start, capture.capturedAt)
        if (duration.isNegative || duration.isZero || duration > Duration.ofHours(24))
            return Result.Failed(Failure(FailureKind.CONFLICT, "attendance_sequence_conflict"))
    }
    return Result.Success(Unit)
}

fun summarizeAttendance(date: LocalDate, entries: List<AttendanceEntry>): AttendanceDay {
    val accepted = entries.filter { it.status == AttendanceStatus.ACCEPTED }
    val start = accepted.singleOrNull { it.capture.kind == AttendanceKind.CLOCK_IN }
    val end = accepted.singleOrNull { it.capture.kind == AttendanceKind.CLOCK_OUT }
    val breakMinutes = (start?.schedule as? ScheduledDay.Work)?.shift?.details?.breakMinutes ?: 0
    val minutes =
        if (start == null || end == null) 0
        else
            (Duration.between(start.capture.capturedAt, end.capture.capturedAt).toMinutes() -
                    breakMinutes)
                .coerceAtLeast(0)
    return AttendanceDay(
        date,
        entries.toList(),
        minutes,
        entries.count { it.status == AttendanceStatus.PENDING },
        (start == null) != (end == null),
    )
}
