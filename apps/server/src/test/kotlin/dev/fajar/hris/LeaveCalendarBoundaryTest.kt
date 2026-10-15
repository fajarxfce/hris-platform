package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.data.datasources.LeaveCalendarDataSource
import dev.fajar.hris.leave.data.models.*
import dev.fajar.hris.leave.data.repositories.StoredLeaveCalendarRepository
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveCalendarBoundaryTest {
    private val company = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 5)
    private val month = YearMonth.of(2026, 10)

    @Test
    fun inconsistentAggregateCannotEscapeAsDomainData() {
        val source = Source()
        val repository = StoredLeaveCalendarRepository(source)
        val valid = LeaveCalendarDayRow(date, 1, 1, 1, 0, 0)
        for (rows in
            listOf(
                listOf(valid.copy(requestCount = 0)),
                listOf(valid.copy(pendingCount = -1, approvedCount = 2)),
                listOf(valid.copy(date = date.plusMonths(1))),
                listOf(valid, valid),
            )) {
            source.days = rows
            assertEquals(
                "database_failure",
                (repository.month(company, null, date, month) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun malformedStoredPortionOrStatusMapsAtTheRepositoryBoundary() {
        val source = Source()
        val repository = StoredLeaveCalendarRepository(source)
        val valid =
            LeaveCalendarEntryRow(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "E1",
                "Example",
                "ANNUAL",
                "Annual leave",
                3,
                "PENDING",
                0,
            )
        for (row in
            listOf(
                valid.copy(portionMask = 0),
                valid.copy(status = "CANCELLED"),
                valid.copy(version = -1),
            )) {
            source.rows = listOf(row)
            assertEquals(
                "database_failure",
                (repository.entries(company, null, date, date, null, 20) as Result.Failed)
                    .failure
                    .code,
            )
        }
    }

    @Test
    fun technicalFailureIsSanitizedAndCancellationIsNotConvertedToFailure() {
        val source = Source()
        val repository = StoredLeaveCalendarRepository(source)
        source.failure = IllegalArgumentException("private driver details")
        val failure = repository.entries(company, null, date, date, null, 20) as Result.Failed
        assertEquals("database_failure", failure.failure.code)
        assertFalse(failure.toString().contains("private driver details"))
        source.failure = CancellationException("cancelled")
        assertThrows(CancellationException::class.java) {
            repository.month(company, null, date, month)
        }
    }

    private class Source : LeaveCalendarDataSource {
        var days: List<LeaveCalendarDayRow> = emptyList()
        var rows: List<LeaveCalendarEntryRow> = emptyList()
        var failure: RuntimeException? = null

        override fun month(
            company: UUID,
            manager: UUID?,
            accessDate: LocalDate,
            from: LocalDate,
            until: LocalDate,
        ): List<LeaveCalendarDayRow> {
            failure?.let { throw it }
            return days
        }

        override fun entries(
            company: UUID,
            manager: UUID?,
            accessDate: LocalDate,
            date: LocalDate,
            after: UUID?,
            limit: Int,
        ): List<LeaveCalendarEntryRow> {
            failure?.let { throw it }
            return rows
        }
    }
}
