package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

/**
 * Exercises source/storage contracts inside the runtime transaction; no export HTTP API is implied.
 */
class ReportSourceCaptureProbe(
    val snapshots: ReportSnapshotRepository,
    val source: ReportSnapshotSourceRepository,
    val transactions: ReportSourceTransactionRunner,
) {
    fun definition(
        actor: Actor,
        kind: ReportKind,
        from: String,
        until: String,
    ): ReportSnapshotCapture {
        val at = Instant.now().truncatedTo(ChronoUnit.MICROS)
        return ReportSnapshotCapture(
            UUID.randomUUID(),
            requireNotNull(actor.companyId),
            actor.accountId,
            ReportSelection(
                kind,
                kind.definitionVersion,
                ReportFilterParameters(from = from, until = until),
            ),
            ReportExportDataset.DETAIL,
            (reportExportColumns(kind, ReportExportDataset.DETAIL) as Result.Success).value,
            at,
            at.plusSeconds(3600),
        )
    }

    fun capture(actor: Actor, c: ReportSnapshotCapture): ReportSnapshot =
        (transactions.runWithReportSources(actor, setOf(c.companyId)) {
                snapshots.begin(c).flatMap { fresh ->
                    assertTrue(fresh)
                    source.captureDetails(c).flatMap { totals ->
                        snapshots.seal(c).map { snapshot ->
                            assertEquals(totals.rows, snapshot.rows)
                            assertEquals(totals.storedBytes, snapshot.storedBytes)
                            snapshot
                        }
                    }
                }
            } as Result.Success)
            .value

    fun rows(actor: Actor, snapshot: ReportSnapshot): List<Map<String, String?>> {
        val rows =
            (transactions.runWithReportSources(actor, setOf(snapshot.capture.companyId)) {
                    snapshots.rows(snapshot, 0, 500)
                } as Result.Success)
                .value
        assertEquals(snapshot.rows, rows.size)
        assertEquals(
            rows.size,
            (encodeReportCsvPart(snapshot.capture.columns, rows.map { it.cells }, true)
                    as Result.Success)
                .value
                .rows,
        )
        return rows.map { row ->
            snapshot.capture.columns
                .map { it.key }
                .zip(
                    row.cells.map { cell ->
                        when (cell) {
                            is ReportExportCell.Text -> cell.value
                            is ReportExportCell.Integer -> cell.value.toString()
                            is ReportExportCell.Decimal -> cell.value.toPlainString()
                            ReportExportCell.Empty -> null
                        }
                    }
                )
                .toMap()
        }
    }
}
