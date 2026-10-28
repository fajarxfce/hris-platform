package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.ReportSnapshotDataSource
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.data.mappers.*
import dev.fajar.hris.reporting.data.repositories.StoredReportSnapshotRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import tools.jackson.module.kotlin.jacksonObjectMapper

class ReportSnapshotRepositoryTest {
    private val json = jacksonObjectMapper()
    private val company = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val at = Instant.parse("2026-10-01T00:00:00.123456Z")
    private val capture =
        (validateReportSnapshotCapture(
                ReportSnapshotCapture(
                    id,
                    company,
                    account,
                    ReportSelection(
                        ReportKind.HEADCOUNT,
                        "headcount.v1",
                        ReportFilterParameters(asOf = "2026-10-01"),
                    ),
                    ReportExportDataset.SUMMARY,
                    listOf(
                        ReportExportColumn("text", ReportExportColumnType.TEXT),
                        ReportExportColumn("number", ReportExportColumnType.INTEGER),
                        ReportExportColumn("amount", ReportExportColumnType.DECIMAL),
                        ReportExportColumn("optional", ReportExportColumnType.TEXT, true),
                    ),
                    at,
                    at.plusSeconds(3600),
                )
            )
                as Result.Success)
            .value
    private val values =
        listOf(
            ReportExportCell.Text("=1+2 🧾"),
            ReportExportCell.Integer(Long.MIN_VALUE),
            ReportExportCell.Decimal(BigDecimal("12345678901234567890.123456789")),
            ReportExportCell.Empty,
        )

    private inner class Source : ReportSnapshotDataSource {
        var header: ReportSnapshotHeaderRow? =
            capture.toSnapshotHeader(json).copy(rows = 1, storedBytes = 100)
        var page = listOf(ReportSnapshotCellsRow(1, values.toSnapshotCells(json)))
        var failure: Exception? = null
        var appends = 0
        var beforeAppend: () -> Unit = {}

        override fun begin(row: ReportSnapshotHeaderRow): Int {
            header = row
            return 1
        }

        override fun append(
            company: UUID,
            account: UUID,
            id: UUID,
            after: Int,
            rows: List<String>,
        ): Int {
            failure?.let { throw it }
            appends++
            beforeAppend()
            page =
                rows.mapIndexed { index, value -> ReportSnapshotCellsRow(after + index + 1, value) }
            return rows.size
        }

        override fun seal(company: UUID, account: UUID, id: UUID) = header

        override fun find(company: UUID, account: UUID, id: UUID): ReportSnapshotHeaderRow? {
            failure?.let { throw it }
            return header
        }

        override fun rows(
            company: UUID,
            account: UUID,
            id: UUID,
            after: Int,
            limit: Int,
            maxBytes: Int,
        ): List<ReportSnapshotCellsRow> {
            assertEquals(REPORT_SNAPSHOT_PAGE_BYTES, maxBytes)
            failure?.let { throw it }
            return page
        }

        override fun deleteExpiredRows(
            company: UUID,
            account: UUID,
            id: UUID,
            at: Instant,
            limit: Int,
        ) = 0
    }

    private val source = Source()
    private val repository = StoredReportSnapshotRepository(source, json)

    private fun snapshot() = (repository.find(company, account, id) as Result.Success).value!!

    @Test
    fun numericCellsSurviveDefaultJsonSettingsAndDetachedPagesPreserveTheirTypes() {
        val snapshot = snapshot()
        val result = (repository.rows(snapshot, 0, 500) as Result.Success).value
        assertEquals(values, result.single().cells)
        assertThrows(UnsupportedOperationException::class.java) { (result as MutableList).clear() }
        assertTrue(json.readTree(source.page.single().cells)[2].isString)
        val input = mutableListOf(values.toMutableList())
        source.beforeAppend = {
            input.single().clear()
            input.clear()
        }
        assertEquals(Result.Success(Unit), repository.append(capture, 0, input))
        assertEquals(values, source.page.single().toReportSnapshotRow(capture.columns, json).cells)
        for (decimal in
            listOf(BigDecimal("1E+9"), BigDecimal("0E-9"), BigDecimal("-0.000000001"))) {
            val cells = values.toMutableList().also { it[2] = ReportExportCell.Decimal(decimal) }
            val restored =
                ReportSnapshotCellsRow(1, cells.toSnapshotCells(json))
                    .toReportSnapshotRow(capture.columns, json)
            assertEquals(cells, restored.cells)
        }
    }

    @Test
    fun scopeDefinitionAndStoredTotalsAreValidatedAtAcquisition() {
        val original = source.header!!
        val candidates =
            listOf(
                original.copy(companyId = UUID.randomUUID()),
                original.copy(accountId = UUID.randomUUID()),
                original.copy(id = UUID.randomUUID()),
                original.copy(rows = null, storedBytes = null),
                original.copy(rows = 100001),
                original.copy(storedBytes = REPORT_SNAPSHOT_MAX_BYTES + 1),
                original.copy(rows = 0),
                original.copy(
                    definition = original.definition.replace("report-export.v1", "report-export.v9")
                ),
                original.copy(columns = "[]"),
                original.copy(expiresAt = at),
                original.copy(definition = "{}"),
            )
        for (invalid in candidates) {
            source.header = invalid
            assertInstanceOf(Result.Failed::class.java, repository.find(company, account, id))
        }
        source.header = null
        assertEquals(Result.Success(null), repository.find(company, account, id))
    }

    @Test
    fun brokenSequencesMissingRowsAndMalformedCellsNeverBecomeAPartialPage() {
        val snapshot = snapshot()
        for (invalid in
            listOf(
                emptyList(),
                listOf(source.page.single().copy(number = 2)),
                source.page + source.page,
                listOf(ReportSnapshotCellsRow(1, "[\"t\",0,\"1.00\",null]")),
                listOf(ReportSnapshotCellsRow(1, "[\"t\",\"1.5\",\"1.00\",null]")),
                listOf(ReportSnapshotCellsRow(1, "[\"t\",\"1\",\"1E+1000000000\",null]")),
                listOf(ReportSnapshotCellsRow(1, "[null,\"1\",\"1\",null]")),
                listOf(ReportSnapshotCellsRow(1, "[]")),
            )) {
            source.page = invalid
            assertInstanceOf(Result.Failed::class.java, repository.rows(snapshot, 0, 500))
        }
        assertInstanceOf(Result.Failed::class.java, repository.rows(snapshot, -1, 1))
        assertInstanceOf(Result.Failed::class.java, repository.rows(snapshot, 0, 501))
    }

    @Test
    fun appendValidatesTheEntireBoundedBatchBeforeAnySourceWrite() {
        assertInstanceOf(
            Result.Failed::class.java,
            repository.append(
                capture,
                0,
                listOf(values, listOf(ReportExportCell.Text("wrong shape"))),
            ),
        )
        assertEquals(0, source.appends)
        assertInstanceOf(
            Result.Failed::class.java,
            repository.append(capture, 0, List(501) { values }),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repository.append(capture, 100000, listOf(values)),
        )
        val big = listOf(ReportExportCell.Text("測".repeat(3000))) + values.drop(1)
        assertEquals(
            "report_snapshot_append_too_large",
            (repository.append(capture, 0, List(500) { big }) as Result.Failed).failure.code,
        )
        assertEquals(0, source.appends)
        assertInstanceOf(
            Result.Failed::class.java,
            repository.deleteExpiredRows(company, account, id, at, 501),
        )
    }

    @Test
    fun databaseFailureClassificationAndCancellationSurviveAcquisitionAndMapping() {
        source.failure =
            DataAccessResourceFailureException(
                "private datasource failure",
                java.sql.SQLException("private connection failure", "08006"),
            )
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_busy")),
            repository.find(company, account, id),
        )
        source.failure = CancellationException()
        assertThrows(CancellationException::class.java) { repository.find(company, account, id) }
        source.failure = InterruptedException()
        assertThrows(InterruptedException::class.java) {
            repository.append(capture, 0, listOf(values))
        }
    }

    @Test
    fun appendAdmitsExactlyOneMiBAndRejectsTheNextByteBeforeWriting() {
        val c =
            capture.copy(columns = listOf(ReportExportColumn("text", ReportExportColumnType.TEXT)))
        val rows =
            MutableList(500) { listOf<ReportExportCell>(ReportExportCell.Text("x".repeat(2090))) }
        rows[499] = listOf(ReportExportCell.Text("x".repeat(3165)))
        assertEquals(
            REPORT_SNAPSHOT_PAGE_BYTES,
            rows
                .map { it.toSnapshotCells(json) }
                .joinToString(",", "[", "]")
                .toByteArray(Charsets.UTF_8)
                .size,
        )
        assertEquals(Result.Success(true), repository.begin(c))
        assertEquals(Result.Success(Unit), repository.append(c, 0, rows))
        assertEquals(1, source.appends)
        rows[499] = listOf(ReportExportCell.Text("x".repeat(3166)))
        assertEquals(
            "report_snapshot_append_too_large",
            (repository.append(c, 0, rows) as Result.Failed).failure.code,
        )
        assertEquals(1, source.appends)
    }
}
