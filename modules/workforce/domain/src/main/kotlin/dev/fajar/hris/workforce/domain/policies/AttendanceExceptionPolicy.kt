package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.DateTimeException
import java.time.LocalDate
import java.util.UUID

/** A continuation retains its company, date window and exception filter. It grants no access. */
fun parseAttendanceExceptionCursor(
    company: UUID,
    from: LocalDate,
    until: LocalDate,
    kind: AttendanceExceptionKind?,
    value: String?,
): Result<AttendanceExceptionCursor?> {
    if (value == null) return Result.Success(null)
    val invalid = Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
    val prefix = "1:$company:$from:$until:${kind?.name ?: "ALL"}:"
    if (value.length > 160 || !value.startsWith(prefix)) return invalid
    val parts = value.removePrefix(prefix).split(':', limit = 3)
    if (parts.size != 2) return invalid
    return try {
        val date = LocalDate.parse(parts[0])
        val employee = UUID.fromString(parts[1])
        if (date !in from..until || date.toString() != parts[0] || employee.toString() != parts[1])
            invalid
        else Result.Success(AttendanceExceptionCursor(date, employee))
    } catch (_: DateTimeException) {
        invalid
    } catch (_: IllegalArgumentException) {
        invalid
    }
}

fun attendanceExceptionCursor(
    company: UUID,
    from: LocalDate,
    until: LocalDate,
    kind: AttendanceExceptionKind?,
    last: AttendanceException,
): String = "1:$company:$from:$until:${kind?.name ?: "ALL"}:${last.workDate}:${last.employeeId}"
