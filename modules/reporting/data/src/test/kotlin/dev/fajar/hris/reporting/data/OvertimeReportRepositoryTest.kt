package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.data.datasources.OvertimeReportDataSource
import dev.fajar.hris.reporting.data.dto.OvertimeReportAggregateRow
import dev.fajar.hris.reporting.data.repositories.StoredOvertimeReportRepository
import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimeReportRepositoryTest {
    private val company = UUID.randomUUID()
    private val from = LocalDate.parse("2026-08-31")
    private val until = LocalDate.parse("2026-10-01")
    private val total = OvertimeReportAggregateRow(null, 3, 1, 2, 450, 200, 100)
    private val months =
        listOf(
            OvertimeReportAggregateRow(LocalDate.parse("2026-09-01"), 2, 1, 1, 300, 100, 100),
            OvertimeReportAggregateRow(until, 1, 1, 1, 150, 100, 0),
        )
    private var data: () -> List<OvertimeReportAggregateRow> = { months.reversed() + total }
    private var selected: String? = null
    private val repo =
        StoredOvertimeReportRepository(
            object : OvertimeReportDataSource {
                override fun aggregate(
                    company: UUID,
                    from: LocalDate,
                    until: LocalDate,
                    status: String?,
                ): List<OvertimeReportAggregateRow> {
                    selected = status
                    return data()
                }
            }
        )

    @Test
    fun totalsRemainDistinctAndEachMonthRetainsItsClippedDrillDownDates() {
        val result = (repo.aggregate(company, from, until, null) as Result.Success).value
        assertEquals(1, result.totals.employments)
        assertEquals(2, result.months.sumOf { it.counts.employments })
        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
            result.months.map { it.month },
        )
        assertEquals(from, result.months.first().from)
        assertEquals(from, result.months.first().until)
        assertEquals(until, result.months.last().from)
        assertEquals(until, result.months.last().until)
        assertEquals(0, result.months.first().counts.requests)
        assertEquals(450, result.totals.requestedMinutes)
        assertEquals(200, result.totals.actualMinutes)
        assertEquals(100, result.totals.approvedMinutes)
    }

    @Test
    fun aBoundedAnnualWindowCanContainThirteenCalendarMonthsIncludingLeapFebruary() {
        data = { listOf(OvertimeReportAggregateRow(null, 0, 0, 0, 0, 0, 0)) }
        val result =
            (repo.aggregate(
                    company,
                    LocalDate.parse("2024-01-31"),
                    LocalDate.parse("2025-01-30"),
                    null,
                ) as Result.Success)
                .value
        assertEquals(13, result.months.size)
        assertEquals(LocalDate.parse("2024-02-29"), result.months[1].until)
        assertEquals(LocalDate.parse("2025-01-30"), result.months.last().until)
    }

    @Test
    fun missingDuplicateOutOfRangeOrIncoherentDataCannotPublishPartialCounts() {
        for (rows in
            listOf(
                emptyList(),
                months,
                months + total + total,
                months + total.copy(requests = 4),
                months + total.copy(employments = 0),
                months + total.copy(actualRequests = 3),
                months + total.copy(actualMinutes = 201),
                months + total.copy(approvedMinutes = 201),
                months + total.copy(requestedMinutes = 0),
                months + total.copy(requests = Long.MAX_VALUE),
                months + total.copy(actualMinutes = -1),
                listOf(total, months[0].copy(month = from), months[1]),
                listOf(
                    total,
                    months[0].copy(month = from.minusMonths(2).withDayOfMonth(1)),
                    months[1],
                ),
                List(15) { total },
            )) {
            data = { rows }
            assertEquals(
                "database_failure",
                (repo.aggregate(company, from, until, null) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun statusFilteredCountsDistinguishUnsubmittedPlansFromActualAndFinalApproval() {
        for (status in OvertimeStatus.entries) {
            val actuals = if (status == OvertimeStatus.PLANNED) 0L else 1L
            val minutes = actuals * 100
            val row =
                OvertimeReportAggregateRow(
                    null,
                    1,
                    1,
                    actuals,
                    180,
                    minutes,
                    if (status == OvertimeStatus.APPROVED) minutes else 0,
                )
            data = { listOf(row, row.copy(month = until)) }
            assertInstanceOf(
                Result.Success::class.java,
                repo.aggregate(company, from, until, status),
            )
            assertEquals(status.name, selected)
        }
        for ((status, row) in
            listOf(
                OvertimeStatus.PLANNED to OvertimeReportAggregateRow(null, 1, 1, 1, 180, 100, 0),
                OvertimeStatus.PENDING to OvertimeReportAggregateRow(null, 1, 1, 0, 180, 0, 0),
                OvertimeStatus.APPROVED to OvertimeReportAggregateRow(null, 1, 1, 1, 180, 100, 0),
                OvertimeStatus.REJECTED to OvertimeReportAggregateRow(null, 1, 1, 1, 180, 100, 100),
                OvertimeStatus.WITHDRAWN to OvertimeReportAggregateRow(null, 1, 1, 1, 180, 100, 100),
            )) {
            data = { listOf(row, row.copy(month = until)) }
            assertEquals(
                "database_failure",
                (repo.aggregate(company, from, until, status) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun cancellationAndInterruptionPreserveTerminationThroughTheReadBoundary() {
        for (failure in listOf(InterruptedException(), CancellationException())) {
            data = { throw failure }
            assertThrows(failure.javaClass) { repo.aggregate(company, from, until, null) }
        }
    }
}
