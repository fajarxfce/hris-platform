package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import java.math.BigDecimal

fun reportSummaryRows(
    capture: ReportSnapshotCapture,
    source: GroupHeadcountCounts,
): Result<List<List<ReportExportCell>>> =
    summary(capture, ReportKind.HEADCOUNT) { normalized ->
        if (
            source.companies.size != normalized.selection.parameters.companies.size ||
                source.companies.map { it.companyId } != normalized.selection.parameters.companies
        )
            return@summary sourceMismatch()
        // Distinct group persons come from the source snapshot, never the sum of company buckets.
        Result.Success(
            listOf(listOf(text("TOTAL"), ReportExportCell.Empty) + source.totals.cells()) +
                source.companies.map {
                    listOf(text("COMPANY"), text(it.companyId)) + it.counts.cells()
                }
        )
    }

fun reportSummaryRows(
    capture: ReportSnapshotCapture,
    source: AttendanceExceptionAggregate,
): Result<List<List<ReportExportCell>>> =
    summary(capture, ReportKind.ATTENDANCE_EXCEPTIONS) {
        if (source.days.size > 31) return@summary sourceMismatch()
        Result.Success(
            listOf(listOf(text("TOTAL"), ReportExportCell.Empty) + source.totals.cells()) +
                source.days.map { listOf(text("DAY"), text(it.date)) + it.counts.cells() }
        )
    }

fun reportSummaryRows(
    capture: ReportSnapshotCapture,
    source: LeaveReportAggregate,
): Result<List<List<ReportExportCell>>> =
    summary(capture, ReportKind.LEAVE) {
        if (source.statuses.size > 5) return@summary sourceMismatch()
        Result.Success(
            listOf(listOf(text("TOTAL"), ReportExportCell.Empty) + source.totals.cells()) +
                source.statuses.map {
                    listOf(text("STATUS"), text(it.status.name)) + it.counts.cells()
                }
        )
    }

fun reportSummaryRows(
    capture: ReportSnapshotCapture,
    source: ExpenseReportAggregate,
): Result<List<List<ReportExportCell>>> =
    summary(capture, ReportKind.EXPENSES) {
        if (source.statuses.size > 6) return@summary sourceMismatch()
        Result.Success(
            listOf(listOf(text("TOTAL"), ReportExportCell.Empty) + source.totals.cells()) +
                source.statuses.map {
                    listOf(text("STATUS"), text(it.status.name)) + it.counts.cells()
                }
        )
    }

fun reportSummaryRows(
    capture: ReportSnapshotCapture,
    source: OvertimeReportAggregate,
): Result<List<List<ReportExportCell>>> =
    summary(capture, ReportKind.OVERTIME) { normalized ->
        if (source.months.size > 13) return@summary sourceMismatch()
        val status = text(normalized.selection.parameters.status)
        Result.Success(
            listOf(
                listOf(
                    status,
                    text("TOTAL"),
                    ReportExportCell.Empty,
                    ReportExportCell.Empty,
                    ReportExportCell.Empty,
                ) + source.totals.cells()
            ) +
                source.months.map {
                    listOf(status, text("MONTH"), text(it.month), text(it.from), text(it.until)) +
                        it.counts.cells()
                }
        )
    }

fun reportSummaryRows(
    capture: ReportSnapshotCapture,
    source: PayrollCostReportAggregate,
): Result<List<List<ReportExportCell>>> =
    summary(capture, ReportKind.PAYROLL_COSTS) {
        if (source.months.size > 12) return@summary sourceMismatch()
        Result.Success(
            listOf(listOf(text("TOTAL"), ReportExportCell.Empty) + source.totals.cells()) +
                source.months.map { listOf(text("MONTH"), text(it.month)) + it.counts.cells() }
        )
    }

fun reportSummaryRows(
    capture: ReportSnapshotCapture,
    source: AttendanceClosingReportAggregate,
): Result<List<List<ReportExportCell>>> =
    summary(capture, ReportKind.ATTENDANCE_CLOSINGS) {
        if (source.months.size > 12) return@summary sourceMismatch()
        Result.Success(
            listOf(
                listOf(text("TOTAL"), ReportExportCell.Empty) +
                    closingCells(null) +
                    source.totals.cells()
            ) +
                source.months.map {
                    listOf(text("MONTH"), text(it.month)) +
                        closingCells(it.closing) +
                        it.counts.cells()
                }
        )
    }

private fun summary(
    capture: ReportSnapshotCapture,
    kind: ReportKind,
    rows: (ReportSnapshotCapture) -> Result<List<List<ReportExportCell>>>,
): Result<List<List<ReportExportCell>>> {
    if (capture.selection.kind != kind || capture.dataset != ReportExportDataset.SUMMARY)
        return sourceMismatch()
    return validateReportExportCapture(capture).flatMap { normalized ->
        rows(normalized).flatMap { values ->
            if (values.size > 33) return@flatMap sourceMismatch()
            val prefix = reportExportPrefixCells(normalized)
            val complete = values.map { java.util.List.copyOf(prefix + it) }
            // The largest summary has a total and at most 32 selected-company buckets.
            encodeReportCsvPart(normalized.columns, complete, true).flatMap { part ->
                if (part.rows != complete.size) sourceMismatch()
                else Result.Success(java.util.List.copyOf(complete))
            }
        }
    }
}

private fun sourceMismatch() =
    Result.Failed(Failure(FailureKind.VALIDATION, "report_export_source_mismatch"))

private fun text(value: Any?): ReportExportCell =
    if (value == null) ReportExportCell.Empty
    else
        ReportExportCell.Text(
            if (value is java.time.Instant) reportExportInstant(value) else value.toString()
        )

private fun numbers(vararg values: Long) = values.map(ReportExportCell::Integer)

private fun money(vararg values: BigDecimal) = values.map(ReportExportCell::Decimal)

private fun HeadcountCounts.cells() =
    numbers(employments, persons, active, probation, suspended, permanent, fixedTerm)

private fun AttendanceExceptionCounts.cells() =
    numbers(
        observedDays,
        affectedDays,
        affectedEmployments,
        pendingDays,
        incompleteDays,
        pendingEvents,
    )

private fun LeaveReportCounts.cells() =
    numbers(requests, employments, halfDays) +
        ReportExportCell.Decimal(BigDecimal.valueOf(halfDays).multiply(BigDecimal("0.5")))

private fun ExpenseReportCounts.cells() =
    numbers(claims, employments, lines) + ReportExportCell.Decimal(amount)

private fun OvertimeReportCounts.cells() =
    numbers(requests, employments, actualRequests, requestedMinutes, actualMinutes, approvedMinutes)

private fun PayrollCostReportCounts.cells() =
    numbers(assessments, employments, revisedAssessments) +
        money(employerCost, taxableGross, employeeDeductions, withheld, takeHome)

private fun AttendanceClosingCounts.cells() =
    numbers(
        closedMonths,
        employmentMonths,
        employments,
        adjustedEmploymentMonths,
        days,
        scheduledDays,
        scheduledMinutes,
    ) + original.cells() + current.cells()

private fun AttendanceClosingFacts.cells() =
    numbers(workedDays, absenceDays, unrecordedDays, offDays, unassignedDays, acceptedMinutes)

private fun closingCells(value: AttendanceClosingReference?) =
    listOf(
        text(value?.periodId),
        text(value?.jobId),
        value?.version?.let(ReportExportCell::Integer) ?: ReportExportCell.Empty,
        text(value?.closedAt),
        text(value?.timezone),
    )
