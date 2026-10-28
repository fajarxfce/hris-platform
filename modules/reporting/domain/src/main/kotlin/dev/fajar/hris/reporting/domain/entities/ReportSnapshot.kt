package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.util.UUID

enum class ReportExportDataset {
    SUMMARY,
    DETAIL,
}

/** The retained schema and selection describe this capture, never a subsequent live read. */
data class ReportSnapshotCapture(
    val id: UUID,
    val companyId: UUID,
    val accountId: UUID,
    val selection: ReportSelection,
    val dataset: ReportExportDataset,
    val columns: List<ReportExportColumn>,
    val evaluatedAt: Instant,
    val expiresAt: Instant,
    val schemaVersion: String = "report-export.v1",
    val formatVersion: String = "report-csv.v1",
)

data class ReportSnapshot(
    val capture: ReportSnapshotCapture,
    val rows: Int,
    /** Bytes of the staged JSON representation, independently bounded from the eventual CSV. */
    val storedBytes: Long,
)

class ReportSnapshotRow(val number: Int, cells: List<ReportExportCell>) {
    val cells: List<ReportExportCell> = java.util.List.copyOf(cells)
}
