package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.*
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.data.mappers.*
import dev.fajar.hris.reporting.data.repositories.StoredReportSnapshotSourceRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import tools.jackson.module.kotlin.jacksonObjectMapper

class ReportSnapshotSourceRepositoryTest {
    private val json = jacksonObjectMapper()
    private val at = Instant.parse("2026-10-01T00:00:00.000000Z")

    private fun capture(kind: ReportKind = ReportKind.LEAVE): ReportSnapshotCapture {
        val months = kind == ReportKind.ATTENDANCE_CLOSINGS
        return ReportSnapshotCapture(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            ReportSelection(
                kind,
                kind.definitionVersion,
                ReportFilterParameters(
                    from = if (months) "2026-09" else "2026-09-30",
                    until = if (months) "2026-10" else "2026-10-02",
                ),
            ),
            ReportExportDataset.DETAIL,
            (reportExportColumns(kind, ReportExportDataset.DETAIL) as Result.Success).value,
            at,
            at.plusSeconds(3600),
        )
    }

    private val capture = capture()
    private var header: ReportSnapshotHeaderRow? = capture.toSnapshotHeader(json)
    private var headerReads = 0
    private var headerFailure: Exception? = null
    private val snapshots =
        object : ReportSnapshotDataSource {
            override fun find(company: UUID, account: UUID, id: UUID): ReportSnapshotHeaderRow? {
                headerReads++
                headerFailure?.let { throw it }
                return header
            }

            override fun begin(row: ReportSnapshotHeaderRow) = error("Unexpected write")

            override fun append(
                company: UUID,
                account: UUID,
                id: UUID,
                after: Int,
                rows: List<String>,
            ) = error("Unexpected append")

            override fun seal(company: UUID, account: UUID, id: UUID) = error("Unexpected seal")

            override fun rows(
                company: UUID,
                account: UUID,
                id: UUID,
                after: Int,
                limit: Int,
                maxBytes: Int,
            ) = error("Unexpected read")

            override fun deleteExpiredRows(
                company: UUID,
                account: UUID,
                id: UUID,
                at: Instant,
                limit: Int,
            ) = error("Unexpected delete")
        }
    private var totals = ReportCaptureTotalsRow(2, 1000, 0, 2)
    private var sourceFailure: Exception? = null
    private val calls = mutableListOf<Pair<ReportKind, ReportDetailCaptureQuery>>()
    private val source =
        object : ReportSnapshotSourceDataSource {
            private fun run(kind: ReportKind, q: ReportDetailCaptureQuery): ReportCaptureTotalsRow {
                calls += kind to q
                sourceFailure?.let { throw it }
                return totals
            }

            override fun captureLeave(query: ReportDetailCaptureQuery) =
                run(ReportKind.LEAVE, query)

            override fun captureExpenses(query: ReportDetailCaptureQuery) =
                run(ReportKind.EXPENSES, query)

            override fun captureClosings(query: ReportDetailCaptureQuery) =
                run(ReportKind.ATTENDANCE_CLOSINGS, query)
        }
    private val repository = StoredReportSnapshotSourceRepository(source, snapshots, json)

    @Test
    fun eachSourceReceivesCanonicalScopeWindowTypedPrefixAndHardLimits() {
        for (kind in
            listOf(ReportKind.LEAVE, ReportKind.EXPENSES, ReportKind.ATTENDANCE_CLOSINGS)) {
            val c = capture(kind)
            header = c.toSnapshotHeader(json)
            assertEquals(Result.Success(ReportCaptureTotals(2, 1000)), repository.captureDetails(c))
            val (called, q) = calls.last()
            assertEquals(kind, called)
            assertEquals(c.companyId, q.company)
            assertEquals(c.accountId, q.account)
            assertEquals(c.id, q.snapshot)
            assertEquals(
                if (kind == ReportKind.ATTENDANCE_CLOSINGS) LocalDate.parse("2026-09-01")
                else LocalDate.parse("2026-09-30"),
                q.from,
            )
            assertEquals(
                if (kind == ReportKind.ATTENDANCE_CLOSINGS) LocalDate.parse("2026-10-01")
                else LocalDate.parse("2026-10-02"),
                q.until,
            )
            assertEquals(100000, q.maximumRows)
            assertEquals(104857600, q.maximumBytes)
            assertEquals(reportExportPrefixCells(c).toSnapshotCells(json), q.prefix)
        }
        totals = ReportCaptureTotalsRow(0, 0, 0, 0)
        header = capture.toSnapshotHeader(json)
        assertEquals(Result.Success(ReportCaptureTotals(0, 0)), repository.captureDetails(capture))
    }

    @Test
    fun changedMissingOrSealedDefinitionNeverAcquiresSourceRows() {
        val original = header!!
        for (candidate in
            listOf(
                null,
                original.copy(rows = 0, storedBytes = 0),
                original.copy(evaluatedAt = at.plusSeconds(1)),
                original.copy(expiresAt = at.plusSeconds(1)),
                original.copy(definition = original.definition.replace("2026-10-02", "2026-10-03")),
            )) {
            header = candidate
            assertEquals(
                "report_snapshot_capture_changed",
                (repository.captureDetails(capture) as Result.Failed).failure.code,
            )
        }
        header = original.copy(companyId = UUID.randomUUID())
        assertInstanceOf(Result.Failed::class.java, repository.captureDetails(capture))
        assertTrue(calls.isEmpty())
        val before = headerReads
        assertInstanceOf(
            Result.Failed::class.java,
            repository.captureDetails(capture.copy(columns = capture.columns.reversed())),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repository.captureDetails(capture.copy(dataset = ReportExportDataset.SUMMARY)),
        )
        assertEquals(before, headerReads)
    }

    @Test
    fun overflowInvalidSourcesAndPartialInsertionCannotBecomeUsableCaptures() {
        for (t in
            listOf(
                ReportCaptureTotalsRow(100001, 1000, 0, 0),
                ReportCaptureTotalsRow(2, REPORT_SNAPSHOT_MAX_BYTES + 1, 0, 0),
            )) {
            totals = t
            assertEquals(
                Result.Failed(Failure(FailureKind.VALIDATION, "report_export_too_large")),
                repository.captureDetails(capture),
            )
        }
        totals = ReportCaptureTotalsRow(2, 1000, 1, 0)
        assertEquals(
            Result.Failed(Failure(FailureKind.UNEXPECTED, "report_export_source_invalid")),
            repository.captureDetails(capture),
        )
        for (t in
            listOf(
                ReportCaptureTotalsRow(2, 1000, 0, 1),
                ReportCaptureTotalsRow(2, 1000, 1, 2),
                ReportCaptureTotalsRow(0, 1, 0, 0),
                ReportCaptureTotalsRow(1, 0, 0, 1),
                ReportCaptureTotalsRow(100002, 1000, 0, 0),
                ReportCaptureTotalsRow(2, -1, 0, 2),
            )) {
            totals = t
            assertInstanceOf(Result.Failed::class.java, repository.captureDetails(capture))
        }
    }

    @Test
    fun acquisitionAndMappingPreserveFailureClassificationAndCancellation() {
        val failure =
            DataAccessResourceFailureException(
                "private detail",
                java.sql.SQLException("private connection", "08006"),
            )
        headerFailure = failure
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_busy")),
            repository.captureDetails(capture),
        )
        headerFailure = null
        sourceFailure = failure
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_busy")),
            repository.captureDetails(capture),
        )
        sourceFailure = CancellationException()
        assertThrows(CancellationException::class.java) { repository.captureDetails(capture) }
        sourceFailure = InterruptedException()
        assertThrows(InterruptedException::class.java) { repository.captureDetails(capture) }
    }
}
