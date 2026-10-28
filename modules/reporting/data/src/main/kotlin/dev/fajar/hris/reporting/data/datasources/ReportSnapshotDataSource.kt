package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import java.time.Instant
import java.util.UUID

interface ReportSnapshotDataSource {
    fun begin(row: ReportSnapshotHeaderRow): Int

    fun append(company: UUID, account: UUID, id: UUID, after: Int, rows: List<String>): Int

    fun seal(company: UUID, account: UUID, id: UUID): ReportSnapshotHeaderRow?

    fun find(company: UUID, account: UUID, id: UUID): ReportSnapshotHeaderRow?

    fun rows(
        company: UUID,
        account: UUID,
        id: UUID,
        after: Int,
        limit: Int,
        maxBytes: Int,
    ): List<ReportSnapshotCellsRow>

    fun deleteExpiredRows(company: UUID, account: UUID, id: UUID, at: Instant, limit: Int): Int
}
