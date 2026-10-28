package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.data.datasources.AttendanceClosingReportDataSource
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.data.repositories.StoredAttendanceClosingReportRepository
import java.time.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AttendanceClosingReportRepositoryTest {
    private val company = UUID.randomUUID()
    private val from = YearMonth.parse("2026-08")
    private val until = YearMonth.parse("2026-11")
    private val september = month(YearMonth.parse("2026-09"))
    private val october = month(YearMonth.parse("2026-10"))
    private val total =
        empty()
            .copy(
                counts =
                    AttendanceClosingCountsRow(
                        2,
                        2,
                        1,
                        2,
                        61,
                        40,
                        19200,
                        AttendanceClosingFactsRow(2, 2, 53, 2, 2, 840),
                        AttendanceClosingFactsRow(4, 2, 51, 2, 2, 1680),
                    )
            )
    private var data: () -> List<AttendanceClosingReportRow> = { listOf(october, total, september) }
    private val repo =
        StoredAttendanceClosingReportRepository(
            object : AttendanceClosingReportDataSource {
                override fun aggregate(
                    company: UUID,
                    from: LocalDate,
                    until: LocalDate,
                ): List<AttendanceClosingReportRow> {
                    assertEquals(this@AttendanceClosingReportRepositoryTest.company, company)
                    assertEquals(1, from.dayOfMonth)
                    assertEquals(1, until.dayOfMonth)
                    return data()
                }
            }
        )

    private fun empty() =
        AttendanceClosingReportRow(null, null, null, null, null, null, AttendanceClosingCountsRow())

    private fun month(month: YearMonth): AttendanceClosingReportRow =
        AttendanceClosingReportRow(
            month.atDay(1),
            UUID.randomUUID(),
            UUID.randomUUID(),
            2,
            OffsetDateTime.parse("2026-12-01T00:00:00Z"),
            "Asia/Jakarta",
            AttendanceClosingCountsRow(
                1,
                1,
                1,
                1,
                month.lengthOfMonth().toLong(),
                20,
                9600,
                AttendanceClosingFactsRow(1, 1, month.lengthOfMonth() - 4L, 1, 1, 420),
                AttendanceClosingFactsRow(2, 1, month.lengthOfMonth() - 5L, 1, 1, 840),
            ),
        )

    @Test
    fun originalAndCurrentFactsReconcileWithCompleteMonthsAndDistinctEmploymentTotals() {
        val result = (repo.aggregate(company, from, until) as Result.Success).value
        assertEquals(
            listOf(from, from.plusMonths(1), from.plusMonths(2), until),
            result.months.map { it.month },
        )
        assertEquals(2, result.totals.closedMonths)
        assertEquals(2, result.totals.employmentMonths)
        assertEquals(1, result.totals.employments)
        assertEquals(840, result.totals.original.acceptedMinutes)
        assertEquals(1680, result.totals.current.acceptedMinutes)
        assertNull(result.months.first().closing)
        assertEquals(0, result.months.first().counts.days)
        assertEquals(september.periodId, result.months[1].closing?.periodId)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.months as MutableList).clear()
        }
    }

    @Test
    fun anEmptyClosedMonthIsDistinctFromAnUnclosedMonthAcrossATwelveMonthWindow() {
        val closed = september.copy(counts = AttendanceClosingCountsRow(closedMonths = 1))
        data = {
            listOf(empty().copy(counts = AttendanceClosingCountsRow(closedMonths = 1)), closed)
        }
        val result = (repo.aggregate(company, from, from.plusMonths(11)) as Result.Success).value
        assertEquals(12, result.months.size)
        assertEquals(1, result.totals.closedMonths)
        assertNotNull(result.months[1].closing)
        assertEquals(0, result.months[1].counts.days)
        assertNull(result.months[0].closing)
    }

    @Test
    fun scheduledMinutesRemainLongAtTheBoundedAnnualMaximumAndOriginalFactsStaySeparate() {
        val months =
            (1..12).map { number ->
                val month = YearMonth.of(2026, number)
                val days = month.lengthOfMonth() * 5000L
                val facts =
                    AttendanceClosingFactsRow(workedDays = days, acceptedMinutes = days * 1440)
                month(month)
                    .copy(
                        counts =
                            AttendanceClosingCountsRow(
                                1,
                                5000,
                                5000,
                                0,
                                days,
                                days,
                                days * 3600,
                                facts,
                                facts,
                            )
                    )
            }
        val days = 365 * 5000L
        val facts = AttendanceClosingFactsRow(workedDays = days, acceptedMinutes = days * 1440)
        data = {
            listOf(
                empty()
                    .copy(
                        counts =
                            AttendanceClosingCountsRow(
                                12,
                                60000,
                                5000,
                                0,
                                days,
                                days,
                                days * 3600,
                                facts,
                                facts,
                            )
                    )
            ) + months
        }
        val result =
            (repo.aggregate(company, YearMonth.parse("2026-01"), YearMonth.parse("2026-12"))
                    as Result.Success)
                .value
        assertEquals(6570000000L, result.totals.scheduledMinutes)
        assertEquals(result.totals.original, result.totals.current)
    }

    @Test
    fun missingClosingReferencesMalformedSourceFactsAndNonReconcilingTotalsFailInsideTheBoundary() {
        val invalid =
            listOf(
                emptyList(),
                listOf(september, october),
                listOf(total, total),
                List(14) { total },
                listOf(total, september.copy(month = from.atDay(2)), october),
                listOf(total, september.copy(month = from.minusMonths(1).atDay(1)), october),
                listOf(total, september.copy(periodId = null), october),
                listOf(total, september.copy(jobId = null), october),
                listOf(total, september.copy(version = 0), october),
                listOf(total, september.copy(closedAt = null), october),
                listOf(total, september.copy(timezone = "invalid"), october),
                listOf(total, september, october.copy(periodId = september.periodId)),
                listOf(total, september, october.copy(jobId = september.jobId)),
                listOf(total.copy(invalidSources = 1), september, october),
                listOf(total.copy(periodId = september.periodId), september, october),
            ) +
                listOf(
                        total.counts.copy(closedMonths = Long.MAX_VALUE),
                        total.counts.copy(employmentMonths = 10001),
                        total.counts.copy(employments = 0),
                        total.counts.copy(employments = 3),
                        total.counts.copy(adjustedEmploymentMonths = 3),
                        total.counts.copy(days = 62),
                        total.counts.copy(scheduledDays = -1),
                        total.counts.copy(scheduledMinutes = 19201),
                        total.counts.copy(original = total.counts.original.copy(workedDays = 3)),
                        total.counts.copy(
                            original = total.counts.original.copy(acceptedMinutes = 2881)
                        ),
                        total.counts.copy(
                            current = total.counts.current.copy(acceptedMinutes = -1)
                        ),
                        total.counts.copy(adjustedEmploymentMonths = 0),
                    )
                    .map { listOf(total.copy(counts = it), september, october) }
        for (rows in invalid) {
            data = { rows }
            assertEquals(
                "database_failure",
                (repo.aggregate(company, from, until) as Result.Failed).failure.code,
                rows.toString(),
            )
        }
    }

    @Test
    fun cancellationAndInterruptsRemainAvailableForOwnedCleanupAndRetry() {
        for (failure in listOf(InterruptedException(), CancellationException())) {
            data = { throw failure }
            assertThrows(failure.javaClass) { repo.aggregate(company, from, until) }
        }
        data = { listOf(total, september, october) }
        assertTrue(repo.aggregate(company, from, until) is Result.Success)
    }
}
