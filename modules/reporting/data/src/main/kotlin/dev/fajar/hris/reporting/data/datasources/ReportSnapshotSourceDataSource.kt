package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*

interface ReportSnapshotSourceDataSource {
    fun captureLeave(query: ReportDetailCaptureQuery): ReportCaptureTotalsRow

    fun captureExpenses(query: ReportDetailCaptureQuery): ReportCaptureTotalsRow

    fun captureClosings(query: ReportDetailCaptureQuery): ReportCaptureTotalsRow
}
