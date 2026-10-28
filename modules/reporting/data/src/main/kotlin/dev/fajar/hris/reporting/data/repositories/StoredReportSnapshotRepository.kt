package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.ReportSnapshotDataSource
import dev.fajar.hris.reporting.data.mappers.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.ReportSnapshotRepository
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredReportSnapshotRepository(
    private val source: ReportSnapshotDataSource,
    private val json: ObjectMapper,
) : ReportSnapshotRepository {
    override fun begin(capture: ReportSnapshotCapture): Result<Boolean> =
        validateReportSnapshotCapture(capture).flatMap {
            safeDatabaseCall {
                source.begin(it.toSnapshotHeader(json)).also { count -> require(count in 0..1) } ==
                    1
            }
        }

    override fun append(
        capture: ReportSnapshotCapture,
        after: Int,
        rows: List<List<ReportExportCell>>,
    ): Result<Unit> {
        val validated = validateReportSnapshotCapture(capture)
        if (validated is Result.Failed) return validated
        val value = (validated as Result.Success).value
        if (
            after !in 0 until REPORT_SNAPSHOT_MAX_ROWS ||
                rows.size !in 1..REPORT_SNAPSHOT_PAGE_ROWS ||
                rows.size > REPORT_SNAPSHOT_MAX_ROWS - after
        )
            return invalidRows()
        // Validate and serialize one row at a time before I/O, retaining at most one MiB per
        // append.
        val prepared = mutableListOf<String>()
        var bytes = 2
        for (row in rows.toList()) {
            if (row.size != value.columns.size) return invalidRows()
            val cells = row.toList()
            val valid = encodeReportCsvPart(value.columns, listOf(cells), false)
            if (valid is Result.Failed) return valid
            val encoded = safeDatabaseCall { cells.toSnapshotCells(json) }
            if (encoded is Result.Failed) return encoded
            val text = (encoded as Result.Success).value
            bytes += text.toByteArray(Charsets.UTF_8).size + if (prepared.isEmpty()) 0 else 1
            if (bytes > REPORT_SNAPSHOT_PAGE_BYTES)
                return Result.Failed(
                    Failure(FailureKind.VALIDATION, "report_snapshot_append_too_large")
                )
            prepared += text
        }
        return safeDatabaseCall {
            require(
                source.append(value.companyId, value.accountId, value.id, after, prepared) ==
                    prepared.size
            )
        }
    }

    override fun seal(capture: ReportSnapshotCapture): Result<ReportSnapshot> =
        validateReportSnapshotCapture(capture).flatMap { value ->
            safeDatabaseCall {
                requireNotNull(source.seal(value.companyId, value.accountId, value.id))
                    .toReportSnapshot(value.companyId, value.accountId, value.id, json)
                    .also { require(it.capture == value) }
            }
        }

    override fun find(company: UUID, account: UUID, id: UUID): Result<ReportSnapshot?> =
        safeDatabaseCall {
            source.find(company, account, id)?.toReportSnapshot(company, account, id, json)
        }

    override fun rows(
        snapshot: ReportSnapshot,
        after: Int,
        limit: Int,
    ): Result<List<ReportSnapshotRow>> {
        if (
            snapshot.rows !in 0..REPORT_SNAPSHOT_MAX_ROWS ||
                after !in 0..snapshot.rows ||
                limit !in 1..REPORT_SNAPSHOT_PAGE_ROWS
        )
            return invalidRows()
        return safeDatabaseCall {
            val c = snapshot.capture
            val rows =
                source.rows(
                    c.companyId,
                    c.accountId,
                    c.id,
                    after,
                    limit,
                    REPORT_SNAPSHOT_PAGE_BYTES,
                )
            require(
                rows.size <= minOf(limit, snapshot.rows - after) &&
                    (rows.isEmpty() == (after == snapshot.rows))
            )
            var bytes = 0L
            java.util.List.copyOf(
                rows.mapIndexed { index, row ->
                    require(row.number == after + index + 1)
                    require(row.cells.length <= REPORT_SNAPSHOT_PAGE_BYTES)
                    bytes += row.cells.toByteArray(Charsets.UTF_8).size
                    require(bytes <= REPORT_SNAPSHOT_PAGE_BYTES)
                    row.toReportSnapshotRow(c.columns, json)
                }
            )
        }
    }

    override fun deleteExpiredRows(
        company: UUID,
        account: UUID,
        id: UUID,
        at: Instant,
        limit: Int,
    ): Result<Int> {
        if (limit !in 1..REPORT_SNAPSHOT_PAGE_ROWS) return invalidRows()
        return safeDatabaseCall {
            source.deleteExpiredRows(company, account, id, at, limit).also {
                require(it in 0..limit)
            }
        }
    }

    private fun invalidRows() =
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_report_snapshot_rows"))
}
