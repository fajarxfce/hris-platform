package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.*
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.data.mappers.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.ReportSnapshotSourceRepository
import java.time.LocalDate
import java.time.YearMonth
import tools.jackson.databind.ObjectMapper

class StoredReportSnapshotSourceRepository(
    private val source: ReportSnapshotSourceDataSource,
    private val snapshots: ReportSnapshotDataSource,
    private val json: ObjectMapper,
) : ReportSnapshotSourceRepository {
    override fun captureDetails(capture: ReportSnapshotCapture): Result<ReportCaptureTotals> {
        if (capture.dataset != ReportExportDataset.DETAIL)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "unsupported_report_export_dataset")
            )
        return validateReportExportCapture(capture).flatMap { c ->
            safeDatabaseCall {
                    val header = snapshots.find(c.companyId, c.accountId, c.id)
                    header != null &&
                        header.rows == null &&
                        header.storedBytes == null &&
                        header.toReportSnapshotCapture(c.companyId, c.accountId, c.id, json) == c
                }
                .flatMap { matches ->
                    if (!matches)
                        return@flatMap Result.Failed(
                            Failure(FailureKind.CONFLICT, "report_snapshot_capture_changed")
                        )
                    safeDatabaseCall {
                            val p = c.selection.parameters
                            val months = c.selection.kind == ReportKind.ATTENDANCE_CLOSINGS
                            fun date(value: String?) =
                                if (months) YearMonth.parse(requireNotNull(value)).atDay(1)
                                else LocalDate.parse(requireNotNull(value))
                            val query =
                                ReportDetailCaptureQuery(
                                    c.companyId,
                                    c.accountId,
                                    c.id,
                                    date(p.from),
                                    date(p.until),
                                    reportExportPrefixCells(c).toSnapshotCells(json),
                                    REPORT_SNAPSHOT_MAX_ROWS,
                                    REPORT_SNAPSHOT_MAX_BYTES,
                                )
                            val totals =
                                when (c.selection.kind) {
                                    ReportKind.LEAVE -> source.captureLeave(query)
                                    ReportKind.EXPENSES -> source.captureExpenses(query)
                                    ReportKind.ATTENDANCE_CLOSINGS -> source.captureClosings(query)
                                    else -> error("Unsupported detail capture")
                                }
                            require(
                                totals.rows in 0..REPORT_SNAPSHOT_MAX_ROWS + 1 &&
                                    totals.storedBytes >= 0 &&
                                    totals.invalidSources >= 0 &&
                                    totals.inserted in 0..REPORT_SNAPSHOT_MAX_ROWS
                            )
                            val rejected =
                                totals.rows > REPORT_SNAPSHOT_MAX_ROWS ||
                                    totals.storedBytes > REPORT_SNAPSHOT_MAX_BYTES ||
                                    totals.invalidSources > 0
                            require(totals.inserted == if (rejected) 0 else totals.rows)
                            require((totals.rows == 0) == (totals.storedBytes == 0L))
                            totals
                        }
                        .flatMap { totals ->
                            when {
                                totals.rows > REPORT_SNAPSHOT_MAX_ROWS ||
                                    totals.storedBytes > REPORT_SNAPSHOT_MAX_BYTES ->
                                    Result.Failed(
                                        Failure(FailureKind.VALIDATION, "report_export_too_large")
                                    )
                                totals.invalidSources > 0 ->
                                    Result.Failed(
                                        Failure(
                                            FailureKind.UNEXPECTED,
                                            "report_export_source_invalid",
                                        )
                                    )
                                else ->
                                    Result.Success(
                                        ReportCaptureTotals(totals.rows, totals.storedBytes)
                                    )
                            }
                        }
                }
        }
    }
}
