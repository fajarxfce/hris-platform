package dev.fajar.hris.reporting.data

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.data.datasources.AttendanceExceptionReportDataSource
import dev.fajar.hris.reporting.data.dto.AttendanceExceptionAggregateRow
import dev.fajar.hris.reporting.data.repositories.StoredAttendanceExceptionReportRepository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AttendanceExceptionReportRepositoryTest {
    private val date = LocalDate.parse("2026-09-01")
    private val total = AttendanceExceptionAggregateRow(null, 2, 2, 1, 2, 1, 3)
    private val days =
        listOf(
            AttendanceExceptionAggregateRow(date, 1, 1, 1, 1, 1, 2),
            AttendanceExceptionAggregateRow(date.plusDays(1), 1, 1, 1, 1, 0, 1),
        )

    private fun read(rows: () -> List<AttendanceExceptionAggregateRow>) =
        StoredAttendanceExceptionReportRepository(
                object : AttendanceExceptionReportDataSource {
                    override fun aggregate(company: UUID, from: LocalDate, until: LocalDate) =
                        rows()
                }
            )
            .aggregate(UUID.randomUUID(), date, date.plusDays(2))

    @Test
    fun distinctEmploymentsAreNotSummedAcrossDaysAndEmptyDatesRemainVisible() {
        val value = (read { days.reversed() + total } as Result.Success).value
        assertEquals(1, value.totals.affectedEmployments)
        assertEquals(2, value.days.sumOf { it.counts.affectedEmployments })
        assertEquals(listOf(date, date.plusDays(1), date.plusDays(2)), value.days.map { it.date })
        assertEquals(0, value.days.last().counts.observedDays)
        assertEquals(3, value.totals.pendingEvents)
    }

    @Test
    fun malformedOrIncoherentAggregatesFailWithoutPublishingPartialCounts() {
        for (rows in
            listOf(
                emptyList(),
                days,
                days + total + total,
                days + total.copy(affectedDays = 1),
                days + total.copy(affectedEmployments = 0),
                days + total.copy(pendingEvents = 4),
                days + total.copy(observedDays = -1),
                days + total.copy(incompleteDays = 3),
                listOf(total, days[0].copy(date = date.minusDays(1)), days[1]),
                listOf(total, days[0].copy(affectedEmployments = 0), days[1]),
                listOf(total, days[0].copy(pendingEvents = Long.MAX_VALUE), days[1]),
                List(33) { total },
            )) {
            assertEquals("database_failure", (read { rows } as Result.Failed).failure.code)
        }
    }

    @Test
    fun interruptedOrCancelledAcquisitionCannotBecomeReportData() {
        for (failure in listOf(InterruptedException(), CancellationException())) assertThrows(
            failure.javaClass
        ) {
            read { throw failure }
        }
    }
}
