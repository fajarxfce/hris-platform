package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveStatus
import dev.fajar.hris.reporting.domain.entities.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

fun validateLeaveReportAccess(actor: Actor): Result<Unit> =
    actor.requirePermission("reports.read").flatMap { actor.requirePermission("leave.read") }

fun validateLeaveReportRange(from: LocalDate, until: LocalDate): Result<Unit> =
    if (
        from.year in 1900..2200 &&
            until.year in 1900..2200 &&
            ChronoUnit.DAYS.between(from, until) in 0..365
    )
        Result.Success(Unit)
    else
        Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_report_range",
                parameters = mapOf("maximumDays" to "366"),
            )
        )

/**
 * Scoped continuations retain an immutable ordering key, never authority or a frozen result set.
 */
fun parseLeaveReportCursor(
    company: UUID,
    from: LocalDate,
    until: LocalDate,
    status: LeaveStatus?,
    value: String?,
): Result<LeaveReportCursor?> {
    if (value == null) return Result.Success(null)
    val invalid = Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
    val prefix = leaveReportCursorPrefix(company, from, until, status)
    if (value.length > 200 || !value.startsWith(prefix)) return invalid
    val parts = value.removePrefix(prefix).split('|', limit = 3)
    if (parts.size != 2) return invalid
    return try {
        val at = Instant.parse(parts[0])
        val id = UUID.fromString(parts[1])
        if (
            at.atOffset(ZoneOffset.UTC).year !in 1..9999 ||
                at.toString() != parts[0] ||
                id.toString() != parts[1]
        )
            invalid
        else Result.Success(LeaveReportCursor(at, id))
    } catch (_: DateTimeException) {
        invalid
    } catch (_: IllegalArgumentException) {
        invalid
    }
}

fun leaveReportCursor(
    company: UUID,
    from: LocalDate,
    until: LocalDate,
    status: LeaveStatus?,
    last: LeaveReportRequest,
): String = "${leaveReportCursorPrefix(company, from, until, status)}${last.submittedAt}|${last.id}"

private fun leaveReportCursorPrefix(
    company: UUID,
    from: LocalDate,
    until: LocalDate,
    status: LeaveStatus?,
): String = "1|$company|$from|$until|${status?.name ?: "ALL"}|"
