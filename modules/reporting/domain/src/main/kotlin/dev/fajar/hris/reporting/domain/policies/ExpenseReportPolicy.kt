package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.expenses.domain.policies.canReadCompanyExpenses
import dev.fajar.hris.reporting.domain.entities.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

fun validateExpenseReportAccess(actor: Actor): Result<Unit> =
    actor.requirePermission("reports.read").flatMap {
        if (canReadCompanyExpenses(actor.permissions)) Result.Success(Unit)
        else Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
    }

fun validateExpenseReportRange(from: LocalDate, until: LocalDate): Result<Unit> =
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
fun parseExpenseReportCursor(
    company: UUID,
    from: LocalDate,
    until: LocalDate,
    status: ExpenseClaimStatus?,
    value: String?,
): Result<ExpenseReportCursor?> {
    if (value == null) return Result.Success(null)
    val invalid = Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
    val prefix = expenseReportCursorPrefix(company, from, until, status)
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
        else Result.Success(ExpenseReportCursor(at, id))
    } catch (_: DateTimeException) {
        invalid
    } catch (_: IllegalArgumentException) {
        invalid
    }
}

fun expenseReportCursor(
    company: UUID,
    from: LocalDate,
    until: LocalDate,
    status: ExpenseClaimStatus?,
    last: ExpenseReportClaim,
): String =
    "${expenseReportCursorPrefix(company, from, until, status)}${last.submittedAt}|${last.id}"

private fun expenseReportCursorPrefix(
    company: UUID,
    from: LocalDate,
    until: LocalDate,
    status: ExpenseClaimStatus?,
): String = "expenses.v1|$company|$from|$until|${status?.name ?: "ALL"}|"
