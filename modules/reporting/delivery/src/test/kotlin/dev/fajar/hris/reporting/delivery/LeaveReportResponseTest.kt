package dev.fajar.hris.reporting.delivery

import dev.fajar.hris.reporting.delivery.mappers.toResponse
import dev.fajar.hris.reporting.domain.entities.LeaveReportCounts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LeaveReportResponseTest {
    @Test
    fun quantitiesRetainExactHalfDaysBeyondFloatingPointPrecision() {
        for ((units, days) in
            listOf(
                0L to "0",
                1L to "0.5",
                2L to "1",
                7L to "3.5",
                9007199254740993L to "4503599627370496.5",
                Long.MAX_VALUE to "4611686018427387903.5",
            )) assertEquals(days, LeaveReportCounts(halfDays = units).toResponse().days)
    }
}
