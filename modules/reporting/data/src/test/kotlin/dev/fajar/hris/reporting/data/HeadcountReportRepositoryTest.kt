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

class HeadcountReportRepositoryTest {
    private fun repository(operation: (Set<UUID>) -> List<HeadcountAggregateRow>) =
        StoredHeadcountReportRepository(
            object : HeadcountReportDataSource {
                override fun count(
                    companies: Set<UUID>,
                    asOf: LocalDate,
                    statuses: Set<String>,
                ): List<HeadcountAggregateRow> {
                    assertEquals(setOf("ACTIVE", "PROBATION", "SUSPENDED"), statuses)
                    return operation(companies)
                }
            }
        )

    @Test
    fun missingBucketsAreZeroAndPersonCountsAreTakenFromTheDistinctAggregate() {
        val repository = repository { companies ->
            val rows =
                listOf(
                    HeadcountAggregateRow("TOTAL", null, 3, 2),
                    HeadcountAggregateRow("STATUS", "ACTIVE", 2, 2),
                    HeadcountAggregateRow("STATUS", "SUSPENDED", 1, 1),
                    HeadcountAggregateRow("CONTRACT", "PERMANENT", 3, 2),
                )
            rows + rows.map { it.copy(companyId = companies.single()) }
        }
        val counts =
            (repository.count(UUID.randomUUID(), LocalDate.parse("2026-10-01")) as Result.Success)
                .value
        assertEquals(3, counts.employments)
        assertEquals(2, counts.persons)
        assertEquals(0, counts.probation)
        assertEquals(0, counts.fixedTerm)
    }

    @Test
    fun incompleteOrUnknownAggregatesFailInsideTheDataBoundary() {
        val malformed =
            listOf(
                emptyList(),
                listOf(HeadcountAggregateRow("TOTAL", null, 1, 1)),
                listOf(HeadcountAggregateRow("TOTAL", null, 0, 1)),
                listOf(
                    HeadcountAggregateRow("TOTAL", null, 0, 0),
                    HeadcountAggregateRow("TOTAL", null, 0, 0),
                ),
                listOf(
                    HeadcountAggregateRow("TOTAL", null, 0, 0),
                    HeadcountAggregateRow("STATUS", "UNKNOWN", 0, 0),
                ),
            )
        for (rows in malformed) {
            val result =
                repository { rows }.count(UUID.randomUUID(), LocalDate.parse("2026-10-01"))
                    as Result.Failed
            assertEquals("database_failure", result.failure.code)
        }
    }

    @Test
    fun cancellationAndInterruptionAreNeverConvertedIntoAReportFailure() {
        for (error in listOf(InterruptedException(), CancellationException())) {
            assertThrows(error.javaClass) {
                repository { throw error }.count(UUID.randomUUID(), LocalDate.parse("2026-10-01"))
            }
        }
    }
}
