package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.data.datasources.PayrollCostReportDataSource
import dev.fajar.hris.reporting.data.dto.PayrollCostReportAggregateRow
import dev.fajar.hris.reporting.data.repositories.StoredPayrollCostReportRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollCostReportRepositoryTest {
    private val company = UUID.randomUUID()
    private val from = YearMonth.parse("2026-08")
    private val until = YearMonth.parse("2026-10")
    private val september =
        PayrollCostReportAggregateRow(
            LocalDate.parse("2026-09-01"),
            2,
            2,
            0,
            BigDecimal("50000000000.01"),
            BigDecimal("40000000000.01"),
            BigDecimal("100.01"),
            BigDecimal("1000.00"),
            BigDecimal("49999998900.00"),
        )
    private val october =
        PayrollCostReportAggregateRow(
            LocalDate.parse("2026-10-01"),
            1,
            1,
            1,
            BigDecimal("2000.02"),
            BigDecimal("2000.02"),
            BigDecimal("200.02"),
            BigDecimal("-500.01"),
            BigDecimal("2300.01"),
        )
    private val total =
        PayrollCostReportAggregateRow(
            null,
            3,
            2,
            1,
            BigDecimal("50000002000.03"),
            BigDecimal("40000002000.03"),
            BigDecimal("300.03"),
            BigDecimal("499.99"),
            BigDecimal("50000001200.01"),
        )
    private var data: () -> List<PayrollCostReportAggregateRow> = {
        listOf(october, total, september)
    }
    private val repo =
        StoredPayrollCostReportRepository(
            object : PayrollCostReportDataSource {
                override fun aggregate(
                    company: UUID,
                    from: LocalDate,
                    until: LocalDate,
                ): List<PayrollCostReportAggregateRow> {
                    assertEquals(this@PayrollCostReportRepositoryTest.company, company)
                    assertEquals(1, from.dayOfMonth)
                    assertEquals(1, until.dayOfMonth)
                    return data()
                }
            }
        )

    @Test
    fun publishedCostsAndSignedRefundsReconcileAcrossMonthsWithoutSummingDistinctEmployments() {
        val result = (repo.aggregate(company, from, until) as Result.Success).value
        assertEquals(listOf(from, from.plusMonths(1), until), result.months.map { it.month })
        assertEquals(3, result.totals.assessments)
        assertEquals(2, result.totals.employments)
        assertEquals(3, result.months.sumOf { it.counts.employments })
        assertEquals(1, result.totals.revisedAssessments)
        assertEquals(BigDecimal("50000002000.03"), result.totals.employerCost)
        assertEquals(BigDecimal("-500.01"), result.months.last().counts.withheld)
        assertEquals(BigDecimal("0.00"), result.months.first().counts.employerCost)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.months as MutableList).clear()
        }
    }

    @Test
    fun emptyCrossYearReportsHaveTwelveCompleteMonthsAndZeroDecimalAmounts() {
        data = {
            listOf(
                total.copy(
                    assessments = 0,
                    employments = 0,
                    revisedAssessments = 0,
                    employerCost = BigDecimal.ZERO,
                    taxableGross = BigDecimal.ZERO,
                    employeeDeductions = BigDecimal.ZERO,
                    withheld = BigDecimal.ZERO,
                    takeHome = BigDecimal.ZERO,
                )
            )
        }
        val result =
            (repo.aggregate(company, YearMonth.parse("2026-08"), YearMonth.parse("2027-07"))
                    as Result.Success)
                .value
        assertEquals(12, result.months.size)
        assertTrue(
            result.months.all {
                it.counts.assessments == 0L && it.counts.takeHome == BigDecimal("0.00")
            }
        )
    }

    @Test
    fun veryLargeTotalsAndSubRupiahRefundsRemainExact() {
        val row =
            total.copy(
                assessments = 1_000_000,
                employments = 1_000_000,
                revisedAssessments = 0,
                employerCost = BigDecimal("9007199254740993.01"),
                taxableGross = BigDecimal("9007199254740993.01"),
                employeeDeductions = BigDecimal.ZERO,
                withheld = BigDecimal("-0.01"),
                takeHome = BigDecimal("9007199254740993.02"),
            )
        data = { listOf(row, row.copy(month = until.atDay(1))) }
        val result = (repo.aggregate(company, from, until) as Result.Success).value
        assertEquals("9007199254740993.01", result.totals.employerCost.toPlainString())
        assertEquals("-0.01", result.totals.withheld.toPlainString())
    }

    @Test
    fun missingOrIncoherentEvidenceCannotProducePlausiblePartialFinancialTotals() {
        for (rows in
            listOf(
                emptyList(),
                listOf(september, october),
                listOf(total, total),
                List(14) { total },
                listOf(total, september.copy(month = from.atDay(2)), october),
                listOf(total, september.copy(month = from.minusMonths(1).atDay(1)), october),
                listOf(total, september.copy(employments = 1), october),
            ) +
                listOf(
                        total.copy(assessments = 4),
                        total.copy(employments = 1),
                        total.copy(revisedAssessments = 4),
                        total.copy(missingValues = 1),
                        total.copy(employerCost = BigDecimal("0.00")),
                        total.copy(employerCost = BigDecimal("50000002000.031")),
                        total.copy(taxableGross = BigDecimal("50000002000.04")),
                        total.copy(taxableGross = BigDecimal("-0.01")),
                        total.copy(employeeDeductions = BigDecimal("300.04")),
                        total.copy(takeHome = BigDecimal("50000002000.04")),
                        total.copy(withheld = BigDecimal("-3600000000000.01")),
                    )
                    .map { listOf(it, september, october) }) {
            data = { rows }
            assertEquals(
                "database_failure",
                (repo.aggregate(company, from, until) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun failureMappingPreservesCancellationAndInterruptionForOwnedCleanup() {
        for (failure in listOf(InterruptedException(), CancellationException())) {
            data = { throw failure }
            assertThrows(failure.javaClass) { repo.aggregate(company, from, until) }
        }
    }
}
