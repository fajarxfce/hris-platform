package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AttendanceReportPolicyTest {
    @Test
    fun sourceAndReportGrantsAreBothRequired() {
        val actor =
            Actor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                emptySet(),
                Instant.now(),
                UUID.randomUUID(),
            )
        for (grants in
            listOf(
                emptySet(),
                setOf("reports.read"),
                setOf("workforce.read"),
                setOf("reports.read", "attendance.self.record", "workforce.team.read"),
            )) assertEquals(
            "access_denied",
            (validateAttendanceReportAccess(actor.copy(permissions = grants)) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            Result.Success(Unit),
            validateAttendanceReportAccess(
                actor.copy(permissions = setOf("reports.read", "workforce.read"))
            ),
        )
    }

    @Test
    fun rangeHasAnInclusiveThirtyOneDayBoundAndExplicitCalendarBounds() {
        val from = LocalDate.parse("2026-09-01")
        for (days in listOf(0L, 30L)) assertEquals(
            Result.Success(Unit),
            validateAttendanceReportRange(from, from.plusDays(days)),
        )
        for ((start, end) in
            listOf(
                from to from.minusDays(1),
                from to from.plusDays(31),
                LocalDate.MIN to LocalDate.MAX,
                LocalDate.parse("1899-12-31") to LocalDate.parse("1900-01-01"),
            )) {
            val failure = validateAttendanceReportRange(start, end) as Result.Failed
            assertEquals("invalid_report_range", failure.failure.code)
            assertEquals("31", failure.failure.parameters["maximumDays"])
        }
    }
}
