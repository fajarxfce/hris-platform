package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import java.time.Duration

const val REPORT_SNAPSHOT_MAX_ROWS = 100_000
const val REPORT_SNAPSHOT_PAGE_ROWS = 500
const val REPORT_SNAPSHOT_PAGE_BYTES = 1024 * 1024
const val REPORT_SNAPSHOT_MAX_BYTES = 100L * 1024 * 1024
const val REPORT_EXPORT_SCHEMA_VERSION = "report-export.v1"
val REPORT_SNAPSHOT_MAX_LIFETIME: Duration = Duration.ofDays(7)

/** Normalize and detach before any wait; this definition supplies no source authority. */
fun validateReportSnapshotCapture(input: ReportSnapshotCapture): Result<ReportSnapshotCapture> {
    if (
        input.schemaVersion != REPORT_EXPORT_SCHEMA_VERSION ||
            input.formatVersion != REPORT_CSV_FORMAT_VERSION
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "unsupported_report_export_format"))
    if (
        input.expiresAt <= input.evaluatedAt ||
            Duration.between(input.evaluatedAt, input.expiresAt) > REPORT_SNAPSHOT_MAX_LIFETIME ||
            input.evaluatedAt.nano % 1000 != 0 ||
            input.expiresAt.nano % 1000 != 0
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_report_snapshot_lifetime"))
    if (
        input.dataset == ReportExportDataset.DETAIL &&
            input.selection.kind !in
                setOf(ReportKind.LEAVE, ReportKind.EXPENSES, ReportKind.ATTENDANCE_CLOSINGS)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "unsupported_report_export_dataset"))
    val selected = validateReportSelection(input.companyId, input.selection)
    if (selected is Result.Failed) return selected
    val columns = java.util.List.copyOf(input.columns)
    return encodeReportCsvPart(columns, emptyList(), true).map {
        input.copy(selection = (selected as Result.Success).value, columns = columns)
    }
}
