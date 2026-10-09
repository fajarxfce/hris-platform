package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.data.datasources.HeadcountReportDataSource
import dev.fajar.hris.reporting.data.dto.HeadcountAggregateRow
import dev.fajar.hris.reporting.data.repositories.StoredHeadcountReportRepository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GroupHeadcountReportRepositoryTest {
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val empty = UUID.randomUUID()
    private val date = LocalDate.parse("2026-10-01")
    private val companies = setOf(first, second, empty)

    private fun rows(company: UUID?, employments: Long, persons: Long) =
        listOf(
            HeadcountAggregateRow("TOTAL", null, employments, persons, company),
            HeadcountAggregateRow("STATUS", "ACTIVE", employments, persons, company),
            HeadcountAggregateRow("CONTRACT", "PERMANENT", employments, persons, company),
        )

    private fun repository(operation: () -> List<HeadcountAggregateRow>) =
        StoredHeadcountReportRepository(
            object : HeadcountReportDataSource {
                override fun count(
                    companies: Set<UUID>,
                    asOf: LocalDate,
                    statuses: Set<String>,
                ): List<HeadcountAggregateRow> {
                    assertEquals(this@GroupHeadcountReportRepositoryTest.companies, companies)
                    assertEquals(date, asOf)
                    assertEquals(setOf("ACTIVE", "PROBATION", "SUSPENDED"), statuses)
                    return operation()
                }
            }
        )

    @Test
    fun distinctGroupPersonsAreNotSummedAndEmptyCompaniesRemainVisible() {
        val counts =
            (repository { rows(null, 5, 3) + rows(first, 3, 2) + rows(second, 2, 2) }
                    .countGroup(companies, date) as Result.Success)
                .value
        assertEquals(5, counts.totals.employments)
        assertEquals(3, counts.totals.persons)
        assertEquals(companies.sorted(), counts.companies.map { it.companyId })
        val buckets = counts.companies.associate { it.companyId to it.counts }
        assertEquals(2, buckets.getValue(first).persons)
        assertEquals(2, buckets.getValue(second).persons)
        assertEquals(0, buckets.getValue(empty).employments)
        assertThrows(UnsupportedOperationException::class.java) {
            (counts.companies as MutableList).clear()
        }
    }

    @Test
    fun emptySnapshotReturnsEverySelectedCompanyWithZeroCounts() {
        val counts =
            (repository { listOf(HeadcountAggregateRow("TOTAL", null, 0, 0)) }
                    .countGroup(companies, date) as Result.Success)
                .value
        assertEquals(0, counts.totals.persons)
        assertEquals(companies.size, counts.companies.size)
        assertTrue(counts.companies.all { it.counts.employments == 0L })
    }

    @Test
    fun foreignScopesInconsistentBucketsAndOverflowFailInsideTheRepository() {
        val valid = rows(null, 5, 3) + rows(first, 3, 2) + rows(second, 2, 2)
        val malformed =
            listOf(
                valid + rows(UUID.randomUUID(), 1, 1),
                rows(null, 6, 3) + rows(first, 3, 2) + rows(second, 2, 2),
                rows(null, 5, 1) + rows(first, 3, 2) + rows(second, 2, 2),
                rows(null, 5, 5) + rows(first, 3, 2) + rows(second, 2, 2),
                rows(null, 5, 3),
                rows(first, 3, 2) + rows(second, 2, 2),
                valid + valid.first(),
                rows(null, Long.MAX_VALUE, 1) + rows(first, Long.MAX_VALUE, 1) + rows(second, 1, 1),
                rows(null, 5, 0) + rows(first, 3, 0) + rows(second, 2, 0),
            )
        for (rows in malformed) {
            val result = repository { rows }.countGroup(companies, date) as Result.Failed
            assertEquals("database_failure", result.failure.code)
        }
    }

    @Test
    fun cancellationIsNeverConvertedIntoAggregateData() {
        for (error in listOf(InterruptedException(), CancellationException())) {
            assertThrows(error.javaClass) { repository { throw error }.countGroup(companies, date) }
        }
    }
}
