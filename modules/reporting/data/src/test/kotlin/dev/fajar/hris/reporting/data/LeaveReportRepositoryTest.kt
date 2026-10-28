package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.leave.domain.entities.LeaveStatus
import dev.fajar.hris.reporting.data.datasources.LeaveReportDataSource
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.data.repositories.StoredLeaveReportRepository
import dev.fajar.hris.reporting.domain.entities.LeaveReportCursor
import dev.fajar.hris.reporting.domain.policies.parseLeaveReportCursor
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveReportRepositoryTest {
    private val company = UUID.randomUUID()
    private val from = LocalDate.parse("2026-10-01")
    private val until = LocalDate.parse("2026-10-31")
    private val total = LeaveReportAggregateRow(null, 3, 2, 7, 0)
    private val groups =
        listOf(
            LeaveReportAggregateRow("PENDING", 1, 1, 1, 0),
            LeaveReportAggregateRow("APPROVED", 2, 2, 6, 0),
        )
    private val first =
        LeaveReportRequestRow(
            UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
            UUID.randomUUID(),
            "E1",
            "Example",
            "AL",
            "Annual",
            "PENDING",
            OffsetDateTime.parse("2026-09-30T00:00:00Z"),
            0,
            1,
            2,
            false,
        )
    private val second = first.copy(id = UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private var aggregateRows: () -> List<LeaveReportAggregateRow> = { groups + total }
    private var requestRows: () -> List<LeaveReportRequestRow> = { listOf(first, second) }
    private var requestedLimit = 0
    private val repo =
        StoredLeaveReportRepository(
            object : LeaveReportDataSource {
                override fun aggregate(company: UUID, from: LocalDate, until: LocalDate) =
                    aggregateRows()

                override fun requests(
                    company: UUID,
                    from: LocalDate,
                    until: LocalDate,
                    status: String?,
                    afterAt: OffsetDateTime?,
                    afterId: UUID?,
                    limit: Int,
                ): List<LeaveReportRequestRow> {
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
        assertEquals(7, result.totals.halfDays)
        assertEquals(LeaveStatus.entries, result.statuses.map { it.status })
        assertEquals(
            0,
            result.statuses.single { it.status == LeaveStatus.CANCELLED }.counts.requests,
        )
    }

    @Test
    fun malformedAggregatesCannotPublishPartialOrInconsistentTotals() {
        for (rows in
            listOf(
                emptyList(),
                groups,
                groups + total + total,
                groups + total.copy(requests = 4),
                groups + total.copy(halfDays = 8),
                groups + total.copy(employments = 1),
                groups + total.copy(employments = 0),
                groups + total.copy(invalidRequests = 1),
                groups + total.copy(requests = Long.MAX_VALUE),
                groups + total.copy(halfDays = -1),
                listOf(total, groups[0].copy(status = "UNKNOWN"), groups[1]),
                listOf(total, groups[0].copy(halfDays = 0), groups[1]),
                List(7) { total },
            )) {
            aggregateRows = { rows }
            assertEquals(
                "database_failure",
                (repo.aggregate(company, from, until) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun paginationUsesFiniteOverfetchAndPostgresUuidOrderWithExactClippedUnits() {
        val page =
            (repo.requests(company, from, until, LeaveStatus.PENDING, null, 1) as Result.Success)
                .value
        assertEquals(2, requestedLimit)
        assertEquals(listOf(first.id), page.items.map { it.id })
        assertEquals(1, page.items.single().halfDays)
        assertEquals(2, page.items.single().requestHalfDays)
        val cursor =
            parseLeaveReportCursor(company, from, until, LeaveStatus.PENDING, page.nextCursor)
                as Result.Success
        assertEquals(LeaveReportCursor(first.submittedAt.toInstant(), first.id), cursor.value)
        requestRows = { listOf(second) }
        val next =
            (repo.requests(company, from, until, LeaveStatus.PENDING, cursor.value, 1)
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
                listOf(first.copy(invalid = true)),
                listOf(first, second.copy(status = "APPROVED")),
                listOf(first.copy(halfDays = 3)),
                listOf(first.copy(version = -1)),
                listOf(first.copy(employeeName = "")),
                listOf(first.copy(status = "UNKNOWN")),
                listOf(first, second, second),
            )) {
            requestRows = { rows }
            assertEquals(
                "database_failure",
                (repo.requests(company, from, until, LeaveStatus.PENDING, null, 1) as Result.Failed)
                    .failure
                    .code,
            )
        }
        requestRows = { listOf(first) }
        assertEquals(
            "database_failure",
            (repo.requests(
                    company,
                    from,
                    until,
                    null,
                    LeaveReportCursor(second.submittedAt.toInstant(), second.id),
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
            assertThrows(error.javaClass) { repo.requests(company, from, until, null, null, 1) }
        }
    }
}
