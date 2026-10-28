package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReportSnapshotPolicyTest {
    private val company = UUID.randomUUID()
    private val at = Instant.parse("2026-10-01T00:00:00.123456Z")
    private val capture =
        ReportSnapshotCapture(
            UUID.randomUUID(),
            company,
            UUID.randomUUID(),
            ReportSelection(
                ReportKind.HEADCOUNT,
                "headcount.v1",
                ReportFilterParameters(asOf = "2026-10-01"),
            ),
            ReportExportDataset.SUMMARY,
            listOf(ReportExportColumn("count", ReportExportColumnType.INTEGER)),
            at,
            at.plusSeconds(604800),
        )

    @Test
    fun captureDetachesItsSchemaAndSelectedCompaniesWithoutInventingSourceAuthority() {
        val source = UUID.randomUUID()
        val ids = mutableListOf(source)
        val columns = capture.columns.toMutableList()
        val value =
            (validateReportSnapshotCapture(
                    capture.copy(
                        columns = columns,
                        selection =
                            capture.selection.copy(
                                parameters = capture.selection.parameters.copy(companies = ids)
                            ),
                    )
                )
                    as Result.Success)
                .value
        ids.clear()
        columns.clear()
        assertEquals(listOf(source), value.selection.parameters.companies)
        assertEquals(capture.columns, value.columns)
        assertThrows(UnsupportedOperationException::class.java) {
            (value.columns as MutableList).clear()
        }
        val fallback = (validateReportSnapshotCapture(capture) as Result.Success).value
        assertEquals(listOf(company), fallback.selection.parameters.companies)
    }

    @Test
    fun timestampsMustRoundTripExactlyAndLifetimeCannotExceedSevenDays() {
        assertInstanceOf(Result.Success::class.java, validateReportSnapshotCapture(capture))
        for (invalid in
            listOf(
                capture.copy(expiresAt = at),
                capture.copy(expiresAt = at.minusSeconds(1)),
                capture.copy(expiresAt = capture.expiresAt.plusNanos(1000)),
                capture.copy(evaluatedAt = at.plusNanos(1)),
                capture.copy(expiresAt = capture.expiresAt.minusNanos(1)),
            )) {
            assertEquals(
                "invalid_report_snapshot_lifetime",
                (validateReportSnapshotCapture(invalid) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun captureRetainsOnlySupportedDatasetAndEncodingDefinitions() {
        for (kind in ReportKind.entries) {
            val p =
                when (kind) {
                    ReportKind.HEADCOUNT -> capture.selection.parameters
                    ReportKind.ATTENDANCE_CLOSINGS,
                    ReportKind.PAYROLL_COSTS ->
                        ReportFilterParameters(from = "2026-10", until = "2026-10")
                    else -> ReportFilterParameters(from = "2026-10-01", until = "2026-10-10")
                }
            val base = capture.copy(selection = ReportSelection(kind, kind.definitionVersion, p))
            assertInstanceOf(Result.Success::class.java, validateReportSnapshotCapture(base))
            val detail =
                validateReportSnapshotCapture(base.copy(dataset = ReportExportDataset.DETAIL))
            assertEquals(
                kind in
                    setOf(ReportKind.LEAVE, ReportKind.EXPENSES, ReportKind.ATTENDANCE_CLOSINGS),
                detail is Result.Success,
            )
        }
        for (invalid in
            listOf(capture.copy(schemaVersion = "unknown"), capture.copy(formatVersion = "csv"))) {
            assertEquals(
                "unsupported_report_export_format",
                (validateReportSnapshotCapture(invalid) as Result.Failed).failure.code,
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            validateReportSnapshotCapture(capture.copy(columns = emptyList())),
        )
    }
}
