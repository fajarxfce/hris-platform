package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.leave.domain.entities.LeaveStatus
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReportSummaryExportPolicyTest {
    private val company = UUID.randomUUID()
    private val at = Instant.parse("2026-10-01T02:03:04.123456Z")

    private fun <T> value(result: Result<T>): T = (result as Result.Success).value

    private fun capture(
        kind: ReportKind,
        dataset: ReportExportDataset = ReportExportDataset.SUMMARY,
    ): ReportSnapshotCapture {
        val p =
            when (kind) {
                ReportKind.HEADCOUNT -> ReportFilterParameters(asOf = "2026-10-01")
                ReportKind.ATTENDANCE_CLOSINGS,
                ReportKind.PAYROLL_COSTS ->
                    ReportFilterParameters(from = "2026-09", until = "2026-10")
                else ->
                    ReportFilterParameters(
                        from = "2026-09-30",
                        until = "2026-10-02",
                        status = if (kind == ReportKind.OVERTIME) "APPROVED" else null,
                    )
            }
        return ReportSnapshotCapture(
            UUID.randomUUID(),
            company,
            UUID.randomUUID(),
            ReportSelection(kind, kind.definitionVersion, p),
            dataset,
            value(reportExportColumns(kind, dataset)),
            at,
            at.plusSeconds(3600),
        )
    }

    private fun row(c: ReportSnapshotCapture, r: List<ReportExportCell>) =
        c.columns.map { it.key }.zip(r).toMap()

    private fun text(v: String) = ReportExportCell.Text(v)

    private fun integer(v: Long) = ReportExportCell.Integer(v)

    private fun decimal(v: String) = ReportExportCell.Decimal(BigDecimal(v))

    @Test
    fun layoutsHaveStableOrderTypesAndOnlyTheSupportedDetails() {
        val sizes =
            mapOf(
                ReportKind.HEADCOUNT to 12,
                ReportKind.ATTENDANCE_EXCEPTIONS to 13,
                ReportKind.LEAVE to 11,
                ReportKind.EXPENSES to 12,
                ReportKind.OVERTIME to 16,
                ReportKind.PAYROLL_COSTS to 16,
                ReportKind.ATTENDANCE_CLOSINGS to 31,
            )
        for ((kind, size) in sizes) {
            val c = capture(kind)
            assertEquals(size, c.columns.size)
            assertEquals(
                listOf("definition_version", "evaluated_at"),
                c.columns.take(2).map { it.key },
            )
            assertEquals(c.columns.size, c.columns.map { it.key }.toSet().size)
            assertInstanceOf(Result.Success::class.java, validateReportExportCapture(c))
            assertThrows(UnsupportedOperationException::class.java) {
                (c.columns as MutableList).clear()
            }
            assertEquals(
                "invalid_report_export_columns",
                (validateReportExportCapture(c.copy(columns = c.columns.reversed()))
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
        for (kind in ReportKind.entries) {
            val size =
                mapOf(
                    ReportKind.LEAVE to 18,
                    ReportKind.EXPENSES to 19,
                    ReportKind.ATTENDANCE_CLOSINGS to 36,
                )[kind]
            if (size == null)
                assertEquals(
                    "unsupported_report_export_dataset",
                    (reportExportColumns(kind, ReportExportDataset.DETAIL) as Result.Failed)
                        .failure
                        .code,
                )
            else assertEquals(size, capture(kind, ReportExportDataset.DETAIL).columns.size)
        }
    }

    @Test
    fun groupTotalKeepsDistinctPersonsAndExactSelectedCompaniesWithoutAddingTheAnchor() {
        val selected =
            listOf(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
            )
        val base = capture(ReportKind.HEADCOUNT)
        val c =
            base.copy(
                selection =
                    base.selection.copy(
                        parameters = base.selection.parameters.copy(companies = selected.reversed())
                    )
            )
        val source =
            GroupHeadcountCounts(
                HeadcountCounts(2, 1, 2, 0, 0, 2, 0),
                selected.map { CompanyHeadcount(it, HeadcountCounts(1, 1, 1, 0, 0, 1, 0)) },
            )
        val rows = value(reportSummaryRows(c, source))
        assertEquals(3, rows.size)
        assertEquals(integer(1), row(c, rows[0])["persons"])
        assertEquals(ReportExportCell.Empty, row(c, rows[0])["company_id"])
        assertEquals(
            selected.map { text(it.toString()) },
            rows.drop(1).map { row(c, it)["company_id"] },
        )
        assertEquals(text("2026-10-01T02:03:04.123456Z"), row(c, rows[0])["evaluated_at"])
        assertInstanceOf(
            Result.Failed::class.java,
            reportSummaryRows(c, GroupHeadcountCounts(source.totals, source.companies.reversed())),
        )
        assertInstanceOf(Result.Failed::class.java, reportSummaryRows(base, source))
        assertEquals(
            2,
            value(
                    reportSummaryRows(
                        base,
                        GroupHeadcountCounts(
                            HeadcountCounts(0, 0, 0, 0, 0, 0, 0),
                            listOf(CompanyHeadcount(company, HeadcountCounts(0, 0, 0, 0, 0, 0, 0))),
                        ),
                    )
                )
                .size,
        )
    }

    @Test
    fun attendanceLeaveAndExpenseRowsRetainUnitsStatusAndExactAmounts() {
        val a = capture(ReportKind.ATTENDANCE_EXCEPTIONS)
        val ac = AttendanceExceptionCounts(4, 3, 1, 2, 1, 7)
        val ar =
            value(
                reportSummaryRows(
                    a,
                    AttendanceExceptionAggregate(
                        ac,
                        listOf(AttendanceExceptionDay(LocalDate.parse("2026-10-01"), ac)),
                    ),
                )
            )
        assertEquals(integer(7), row(a, ar[0])["pending_events"])
        assertEquals(text("2026-10-01"), row(a, ar[1])["date"])
        val l = capture(ReportKind.LEAVE)
        val lc = LeaveReportCounts(2, 1, 3)
        val lr =
            value(
                reportSummaryRows(
                    l,
                    LeaveReportAggregate(lc, listOf(LeaveStatusCounts(LeaveStatus.PENDING, lc))),
                )
            )
        assertEquals(integer(3), row(l, lr[0])["half_days"])
        assertEquals(decimal("1.5"), row(l, lr[0])["days"])
        assertEquals(text("PENDING"), row(l, lr[1])["status"])
        val e = capture(ReportKind.EXPENSES)
        val ec = ExpenseReportCounts(2, 1, 3, BigDecimal("9007199254740993.01"))
        val er =
            value(
                reportSummaryRows(
                    e,
                    ExpenseReportAggregate(
                        ec,
                        listOf(ExpenseClaimStatusCounts(ExpenseClaimStatus.RETURNED, ec)),
                    ),
                )
            )
        assertEquals(decimal("9007199254740993.01"), row(e, er[1])["amount"])
        assertEquals(text("IDR"), row(e, er[0])["currency"])
        assertEquals(text("RETURNED"), row(e, er[1])["status"])
        for ((c, rows) in listOf(a to ar, l to lr, e to er)) {
            assertEquals(rows.size, value(encodeReportCsvPart(c.columns, rows, true)).rows)
            assertThrows(UnsupportedOperationException::class.java) {
                (rows as MutableList).clear()
            }
            assertThrows(UnsupportedOperationException::class.java) {
                (rows.first() as MutableList).clear()
            }
        }
    }

    @Test
    fun overtimeKeepsClippedDatesAndPayrollKeepsSignedDecimalWithholding() {
        val o = capture(ReportKind.OVERTIME)
        val oc = OvertimeReportCounts(2, 1, 1, 180, 90, 90)
        val or =
            value(
                reportSummaryRows(
                    o,
                    OvertimeReportAggregate(
                        oc,
                        listOf(
                            OvertimeReportMonth(
                                YearMonth.parse("2026-09"),
                                LocalDate.parse("2026-09-30"),
                                LocalDate.parse("2026-09-30"),
                                oc,
                            )
                        ),
                    ),
                )
            )
        val month = row(o, or[1])
        assertEquals(text("APPROVED"), month["selected_status"])
        assertEquals(text("2026-09-30"), month["month_from"])
        assertEquals(text("2026-09-30"), month["month_until"])
        assertEquals(integer(90), month["approved_minutes"])
        val p = capture(ReportKind.PAYROLL_COSTS)
        val pc =
            PayrollCostReportCounts(
                2,
                1,
                1,
                BigDecimal("100.01"),
                BigDecimal("90.01"),
                BigDecimal("2.00"),
                BigDecimal("-1.99"),
                BigDecimal("90.00"),
            )
        val pr =
            value(
                reportSummaryRows(
                    p,
                    PayrollCostReportAggregate(
                        pc,
                        listOf(PayrollCostReportMonth(YearMonth.parse("2026-09"), pc)),
                    ),
                )
            )
        assertEquals(decimal("-1.99"), row(p, pr[0])["withheld"])
        assertEquals(decimal("100.01"), row(p, pr[1])["employer_cost"])
        assertEquals(text("IDR"), row(p, pr[1])["currency"])
    }

    @Test
    fun closingRowsRetainPublicationIdentityAndBothOriginalAndLatestFacts() {
        val c = capture(ReportKind.ATTENDANCE_CLOSINGS)
        val reference =
            AttendanceClosingReference(UUID.randomUUID(), UUID.randomUUID(), 7, at, "Asia/Jakarta")
        val counts =
            AttendanceClosingCounts(
                1,
                2,
                2,
                1,
                60,
                40,
                19200,
                AttendanceClosingFacts(1, 2, 3, 4, 50, 480),
                AttendanceClosingFacts(2, 1, 3, 4, 50, 900),
            )
        val rows =
            value(
                reportSummaryRows(
                    c,
                    AttendanceClosingReportAggregate(
                        counts,
                        listOf(
                            AttendanceClosingReportMonth(
                                YearMonth.parse("2026-09"),
                                reference,
                                counts,
                            ),
                            AttendanceClosingReportMonth(
                                YearMonth.parse("2026-10"),
                                null,
                                AttendanceClosingCounts(),
                            ),
                        ),
                    ),
                )
            )
        val total = row(c, rows[0])
        val published = row(c, rows[1])
        val missing = row(c, rows[2])
        assertEquals(ReportExportCell.Empty, total["period_id"])
        assertEquals(text(reference.jobId.toString()), published["closing_job_id"])
        assertEquals(integer(7), published["closing_version"])
        assertEquals(integer(480), total["original_accepted_minutes"])
        assertEquals(integer(900), published["current_accepted_minutes"])
        assertEquals(ReportExportCell.Empty, missing["period_id"])
        val empty =
            value(
                reportSummaryRows(
                    c,
                    AttendanceClosingReportAggregate(
                        AttendanceClosingCounts(closedMonths = 1),
                        listOf(
                            AttendanceClosingReportMonth(
                                YearMonth.parse("2026-09"),
                                reference,
                                AttendanceClosingCounts(closedMonths = 1),
                            )
                        ),
                    ),
                )
            )
        assertEquals(text(reference.periodId.toString()), row(c, empty[1])["period_id"])
        assertEquals(integer(0), row(c, empty[1])["employment_months"])
    }

    @Test
    fun wrongSourceUnsupportedVersionAndUnboundedBucketListsFailBeforeProducingRows() {
        val c = capture(ReportKind.LEAVE)
        val source = LeaveReportAggregate(LeaveReportCounts(), emptyList())
        assertInstanceOf(
            Result.Failed::class.java,
            reportSummaryRows(c.copy(dataset = ReportExportDataset.DETAIL), source),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            reportSummaryRows(c.copy(schemaVersion = "unknown"), source),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            reportSummaryRows(capture(ReportKind.EXPENSES), source),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            reportSummaryRows(
                c,
                LeaveReportAggregate(
                    LeaveReportCounts(),
                    List(6) { LeaveStatusCounts(LeaveStatus.PENDING, LeaveReportCounts()) },
                ),
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            reportSummaryRows(
                capture(ReportKind.ATTENDANCE_EXCEPTIONS),
                AttendanceExceptionAggregate(
                    AttendanceExceptionCounts(),
                    List(32) {
                        AttendanceExceptionDay(
                            LocalDate.parse("2026-10-01"),
                            AttendanceExceptionCounts(),
                        )
                    },
                ),
            ),
        )
    }
}
