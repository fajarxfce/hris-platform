package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder

private val exportInstantFormat = DateTimeFormatterBuilder().appendInstant(6).toFormatter()

fun reportExportInstant(value: Instant): String = exportInstantFormat.format(value)

/** Server-owned ordered layouts; an export request never selects arbitrary columns. */
fun reportExportColumns(
    kind: ReportKind,
    dataset: ReportExportDataset,
): Result<List<ReportExportColumn>> {
    val prefix =
        listOf(text("definition_version"), text("evaluated_at")) +
            if (kind == ReportKind.HEADCOUNT) listOf(text("as_of"))
            else
                listOf(text("company_id"), text("from"), text("until")) +
                    if (kind in setOf(ReportKind.EXPENSES, ReportKind.PAYROLL_COSTS))
                        listOf(text("currency"))
                    else emptyList()
    val columns =
        if (dataset == ReportExportDataset.SUMMARY)
            when (kind) {
                ReportKind.HEADCOUNT ->
                    listOf(text("row_type"), text("company_id", true)) +
                        integers(
                            "employments",
                            "persons",
                            "active",
                            "probation",
                            "suspended",
                            "permanent",
                            "fixed_term",
                        )
                ReportKind.ATTENDANCE_EXCEPTIONS ->
                    listOf(text("row_type"), text("date", true)) +
                        integers(
                            "observed_days",
                            "affected_days",
                            "affected_employments",
                            "pending_days",
                            "incomplete_days",
                            "pending_events",
                        )
                ReportKind.LEAVE ->
                    listOf(text("row_type"), text("status", true)) +
                        integers("requests", "employments", "half_days") +
                        decimal("days")
                ReportKind.EXPENSES ->
                    listOf(text("row_type"), text("status", true)) +
                        integers("claims", "employments", "lines") +
                        decimal("amount")
                ReportKind.OVERTIME ->
                    listOf(
                        text("selected_status", true),
                        text("row_type"),
                        text("month", true),
                        text("month_from", true),
                        text("month_until", true),
                    ) +
                        integers(
                            "requests",
                            "employments",
                            "actual_requests",
                            "requested_minutes",
                            "actual_minutes",
                            "approved_minutes",
                        )
                ReportKind.PAYROLL_COSTS ->
                    listOf(text("row_type"), text("month", true)) +
                        integers("assessments", "employments", "revised_assessments") +
                        listOf(
                                "employer_cost",
                                "taxable_gross",
                                "employee_deductions",
                                "withheld",
                                "take_home",
                            )
                            .map(::decimal)
                ReportKind.ATTENDANCE_CLOSINGS ->
                    listOf(text("row_type"), text("month", true)) +
                        closingReferenceColumns(true) +
                        closingCountColumns()
            }
        else
            when (kind) {
                ReportKind.LEAVE ->
                    listOf(
                            "request_id",
                            "employee_id",
                            "employee_number",
                            "employee_name",
                            "type_code",
                            "type_name",
                            "status",
                            "submitted_at",
                        )
                        .map { text(it) } +
                        integers("version", "half_days") +
                        decimal("days") +
                        integer("request_half_days") +
                        decimal("request_days")
                ReportKind.EXPENSES ->
                    listOf(text("claim_id"), text("submission_id"), integer("submission_number")) +
                        listOf(
                                "employee_id",
                                "employee_number",
                                "employee_name",
                                "title",
                                "status",
                                "submitted_at",
                            )
                            .map { text(it) } +
                        integers("version", "lines") +
                        decimal("amount") +
                        decimal("submission_amount")
                ReportKind.ATTENDANCE_CLOSINGS ->
                    listOf("month", "employee_id", "employee_number", "employee_name").map {
                        text(it)
                    } +
                        closingReferenceColumns(false) +
                        listOf(
                            text("adjustment_id", true),
                            ReportExportColumn(
                                "adjustment_revision",
                                ReportExportColumnType.INTEGER,
                                true,
                            ),
                            text("adjusted_at", true),
                        ) +
                        closingCountColumns()
                else ->
                    return Result.Failed(
                        Failure(FailureKind.VALIDATION, "unsupported_report_export_dataset")
                    )
            }
    return Result.Success(java.util.List.copyOf(prefix + columns))
}

fun validateReportExportCapture(input: ReportSnapshotCapture): Result<ReportSnapshotCapture> =
    validateReportSnapshotCapture(input).flatMap { capture ->
        reportExportColumns(capture.selection.kind, capture.dataset).flatMap { expected ->
            if (capture.columns == expected) Result.Success(capture)
            else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_report_export_columns"))
        }
    }

/** Common cells for a validated export capture, shared by summary and SQL detail acquisition. */
fun reportExportPrefixCells(capture: ReportSnapshotCapture): List<ReportExportCell> {
    val p = capture.selection.parameters
    val values =
        listOf(capture.selection.definitionVersion, reportExportInstant(capture.evaluatedAt)) +
            if (capture.selection.kind == ReportKind.HEADCOUNT) listOf(requireNotNull(p.asOf))
            else
                listOf(
                    capture.companyId.toString(),
                    requireNotNull(p.from),
                    requireNotNull(p.until),
                ) +
                    if (
                        capture.selection.kind in
                            setOf(ReportKind.EXPENSES, ReportKind.PAYROLL_COSTS)
                    )
                        listOf("IDR")
                    else emptyList()
    return java.util.List.copyOf(values.map(ReportExportCell::Text))
}

private fun closingReferenceColumns(nullable: Boolean) =
    listOf(
        text("period_id", nullable),
        text("closing_job_id", nullable),
        ReportExportColumn("closing_version", ReportExportColumnType.INTEGER, nullable),
        text("closed_at", nullable),
        text("timezone", nullable),
    )

private fun closingCountColumns() =
    integers(
        "closed_months",
        "employment_months",
        "employments",
        "adjusted_employment_months",
        "days",
        "scheduled_days",
        "scheduled_minutes",
    ) +
        listOf("original", "current").flatMap { prefix ->
            integers(
                "${prefix}_worked_days",
                "${prefix}_absence_days",
                "${prefix}_unrecorded_days",
                "${prefix}_off_days",
                "${prefix}_unassigned_days",
                "${prefix}_accepted_minutes",
            )
        }

private fun text(key: String, nullable: Boolean = false) =
    ReportExportColumn(key, ReportExportColumnType.TEXT, nullable)

private fun integer(key: String) = ReportExportColumn(key, ReportExportColumnType.INTEGER)

private fun decimal(key: String) = ReportExportColumn(key, ReportExportColumnType.DECIMAL)

private fun integers(vararg keys: String) = keys.map(::integer)
