package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.reporting.data.datasources.ExpenseReportDataSource
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.data.repositories.StoredExpenseReportRepository
import dev.fajar.hris.reporting.domain.entities.ExpenseReportCursor
import dev.fajar.hris.reporting.domain.policies.parseExpenseReportCursor
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpenseReportRepositoryTest {
    private val company = UUID.randomUUID()
    private val from = LocalDate.parse("2026-10-01")
    private val until = LocalDate.parse("2026-10-31")
    private val total = ExpenseReportAggregateRow(null, 3, 2, 7, BigDecimal("1000000000000.03"))
    private val groups =
        listOf(
            ExpenseReportAggregateRow("PENDING", 1, 1, 1, BigDecimal("0.01")),
            ExpenseReportAggregateRow("APPROVED", 2, 2, 6, BigDecimal("1000000000000.02")),
        )
    private val first =
        ExpenseReportClaimRow(
            UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            "E1",
            "Example",
            "Travel",
            "PENDING",
            OffsetDateTime.parse("2026-09-30T00:00:00Z"),
            1,
            1,
            BigDecimal("0.01"),
            BigDecimal("123.45"),
        )
    private val second = first.copy(id = UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private var aggregateRows: () -> List<ExpenseReportAggregateRow> = { groups + total }
    private var requestRows: () -> List<ExpenseReportClaimRow> = { listOf(first, second) }
    private var requestedLimit = 0
    private val repo =
        StoredExpenseReportRepository(
            object : ExpenseReportDataSource {
                override fun aggregate(company: UUID, from: LocalDate, until: LocalDate) =
                    aggregateRows()

                override fun claims(
                    company: UUID,
                    from: LocalDate,
                    until: LocalDate,
                    status: String?,
                    afterAt: OffsetDateTime?,
                    afterId: UUID?,
                    limit: Int,
                ): List<ExpenseReportClaimRow> {
                    requestedLimit = limit
                    return requestRows()
                }
            }
        )

    @Test
    fun oneEmployeeAcrossStatusesIsDistinctAndMissingStatusesAreZero() {
        val result = (repo.aggregate(company, from, until) as Result.Success).value
        assertEquals(2, result.totals.employments)
        assertEquals(3, result.statuses.sumOf { it.counts.employments })
        assertEquals(BigDecimal("1000000000000.03"), result.totals.amount)
        assertEquals(7, result.totals.lines)
        assertEquals(ExpenseClaimStatus.entries, result.statuses.map { it.status })
        assertEquals(
            0,
            result.statuses.single { it.status == ExpenseClaimStatus.CANCELLED }.counts.claims,
        )
    }

    @Test
    fun malformedAggregatesCannotPublishPartialOrInconsistentTotals() {
        for (rows in
            listOf(
                emptyList(),
                groups,
                groups + total + total,
                groups + total.copy(claims = 4),
                groups + total.copy(amount = BigDecimal("1000000000000.04")),
                groups + total.copy(employments = 1),
                groups + total.copy(employments = 0),
                groups + total.copy(lines = 0),
                groups + total.copy(claims = Long.MAX_VALUE),
                groups + total.copy(amount = BigDecimal("-0.01")),
                listOf(total, groups[0].copy(status = "UNKNOWN"), groups[1]),
                listOf(total, groups[0].copy(amount = BigDecimal("0.001")), groups[1]),
                List(8) { total },
            )) {
            aggregateRows = { rows }
            assertEquals(
                "database_failure",
                (repo.aggregate(company, from, until) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun paginationUsesFiniteOverfetchAndPostgresUuidOrderWithExactClippedAmounts() {
        val page =
            (repo.claims(company, from, until, ExpenseClaimStatus.PENDING, null, 1)
                    as Result.Success)
                .value
        assertEquals(2, requestedLimit)
        assertEquals(listOf(first.id), page.items.map { it.id })
        assertEquals(BigDecimal("0.01"), page.items.single().amount)
        assertEquals(BigDecimal("123.45"), page.items.single().submissionAmount)
        val cursor =
            parseExpenseReportCursor(
                company,
                from,
                until,
                ExpenseClaimStatus.PENDING,
                page.nextCursor,
            )
                as Result.Success
        assertEquals(ExpenseReportCursor(first.submittedAt.toInstant(), first.id), cursor.value)
        requestRows = { listOf(second) }
        val next =
            (repo.claims(company, from, until, ExpenseClaimStatus.PENDING, cursor.value, 1)
                    as Result.Success)
                .value
        assertEquals(listOf(second.id), next.items.map { it.id })
        assertNull(next.nextCursor)
    }

    @Test
    fun invalidOverfetchRowsOrderOrScopeFailInsideTheMappingBoundary() {
        for (rows in
            listOf(
                listOf(first, first),
                listOf(second, first),
                listOf(first.copy(submissionNumber = 0)),
                listOf(first, second.copy(status = "APPROVED")),
                listOf(first.copy(amount = BigDecimal("123.46"))),
                listOf(first.copy(version = -1)),
                listOf(first.copy(employeeName = "")),
                listOf(first.copy(status = "UNKNOWN")),
                listOf(first, second, second),
            )) {
            requestRows = { rows }
            assertEquals(
                "database_failure",
                (repo.claims(company, from, until, ExpenseClaimStatus.PENDING, null, 1)
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
        requestRows = { listOf(first) }
        assertEquals(
            "database_failure",
            (repo.claims(
                    company,
                    from,
                    until,
                    null,
                    ExpenseReportCursor(second.submittedAt.toInstant(), second.id),
                    1,
                ) as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun cancellationAndInterruptionPropagateFromBothProjections() {
        for (error in listOf(InterruptedException(), CancellationException())) {
            aggregateRows = { throw error }
            requestRows = { throw error }
            assertThrows(error.javaClass) { repo.aggregate(company, from, until) }
            assertThrows(error.javaClass) { repo.claims(company, from, until, null, null, 1) }
        }
    }
}
