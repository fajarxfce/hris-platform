package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import java.time.YearMonth
import java.util.UUID

fun attendanceClosingEmployeeCursor(company: UUID, month: YearMonth, employee: UUID): String =
    "attendance-closings.v1|$company|$month|$employee"

fun parseAttendanceClosingEmployeeCursor(
    company: UUID,
    month: YearMonth,
    value: String?,
): Result<UUID?> {
    if (value == null) return Result.Success(null)
    val invalid = Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
    if (value.length > 110) return invalid
    return try {
        val id = UUID.fromString(value.substringAfterLast('|'))
        if (attendanceClosingEmployeeCursor(company, month, id) != value) invalid
        else Result.Success(id)
    } catch (_: IllegalArgumentException) {
        invalid
    }
}
