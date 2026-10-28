package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import java.time.*
import java.util.UUID

/** A canonical selection carries parameters only, never authority to its source. */
fun validateReportSelection(company: UUID, input: ReportSelection): Result<ReportSelection> {
    if (input.definitionVersion != input.kind.definitionVersion)
        return Result.Failed(Failure(FailureKind.VALIDATION, "unsupported_report_definition"))
    val p = input.parameters
    val invalid = Result.Failed(Failure(FailureKind.VALIDATION, "invalid_report_filter_parameters"))
    if (
        p.companies.size > 32 ||
            listOfNotNull(p.asOf, p.from, p.until).any { it.length > 10 } ||
            (p.status?.length ?: 0) > 32
    )
        return invalid
    if (input.kind == ReportKind.HEADCOUNT) {
        if (p.asOf == null || p.from != null || p.until != null || p.status != null) return invalid
        val date = reportFilterDate(p.asOf) ?: return invalid
        val selected =
            validateHeadcountCompanies(if (p.companies.isEmpty()) listOf(company) else p.companies)
        if (selected is Result.Failed) return selected
        return validateHeadcountDate(date).map {
            input.copy(
                parameters =
                    p.copy(companies = java.util.List.copyOf((selected as Result.Success).value))
            )
        }
    }
    if (p.asOf != null || p.companies.isNotEmpty() || p.from == null || p.until == null)
        return invalid
    if (
        p.status != null &&
            (input.kind != ReportKind.OVERTIME ||
                OvertimeStatus.entries.none { it.name == p.status })
    )
        return invalid
    val range =
        when (input.kind) {
            ReportKind.ATTENDANCE_CLOSINGS,
            ReportKind.PAYROLL_COSTS -> {
                val from = reportFilterMonth(p.from) ?: return invalid
                val until = reportFilterMonth(p.until) ?: return invalid
                if (input.kind == ReportKind.ATTENDANCE_CLOSINGS)
                    validateAttendanceClosingReportRange(from, until)
                else validatePayrollCostReportRange(from, until)
            }
            else -> {
                val from = reportFilterDate(p.from) ?: return invalid
                val until = reportFilterDate(p.until) ?: return invalid
                when (input.kind) {
                    ReportKind.ATTENDANCE_EXCEPTIONS -> validateAttendanceReportRange(from, until)
                    ReportKind.LEAVE -> validateLeaveReportRange(from, until)
                    ReportKind.EXPENSES -> validateExpenseReportRange(from, until)
                    ReportKind.OVERTIME -> validateOvertimeReportRange(from, until)
                }
            }
        }
    return range.map { input.copy(parameters = p.copy(companies = emptyList())) }
}

private fun reportFilterDate(value: String): LocalDate? =
    try {
        if (value.length != 10) null else LocalDate.parse(value).takeIf { it.toString() == value }
    } catch (_: DateTimeException) {
        null
    }

private fun reportFilterMonth(value: String): YearMonth? =
    try {
        if (value.length != 7) null else YearMonth.parse(value).takeIf { it.toString() == value }
    } catch (_: DateTimeException) {
        null
    }
