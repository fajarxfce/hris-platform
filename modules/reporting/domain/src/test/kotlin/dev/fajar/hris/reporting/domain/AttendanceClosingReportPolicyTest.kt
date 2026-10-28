package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AttendanceClosingReportPolicyTest {
    @Test
    fun companyWorkforceAndReportGrantsAreBothRequired() {
        val actor =
            Actor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                setOf("reports.read", "workforce.read"),
                Instant.now(),
                UUID.randomUUID(),
            )
        assertEquals(Result.Success(Unit), validateAttendanceReportAccess(actor))
        for (permissions in
            listOf(
                emptySet(),
                setOf("reports.read"),
                setOf("workforce.read"),
                setOf(
                    "reports.read",
                    "attendance.self.record",
                    "workforce.team.read",
                    "workforce.close",
                    "attendance.correct",
                    "attendance.verify",
                ),
            )) assertEquals(
            "access_denied",
            (validateAttendanceReportAccess(actor.copy(permissions = permissions)) as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun annualMonthBoundsUseClosingYearsAndPermitCrossYearWindows() {
        for ((from, until) in
            listOf(
                "2000-01" to "2000-12",
                "2026-08" to "2027-07",
                "2100-12" to "2100-12",
            )) assertEquals(
            Result.Success(Unit),
            validateAttendanceClosingReportRange(YearMonth.parse(from), YearMonth.parse(until)),
        )
        for ((from, until) in
            listOf(
                "1999-12" to "2000-01",
                "2100-12" to "2101-01",
                "2026-01" to "2027-01",
                "2026-02" to "2026-01",
            )) {
            val failed =
                validateAttendanceClosingReportRange(YearMonth.parse(from), YearMonth.parse(until))
                    as Result.Failed
            assertEquals("invalid_report_month_range", failed.failure.code)
            assertEquals(
                mapOf("maximumMonths" to "12", "minimumYear" to "2000", "maximumYear" to "2100"),
                failed.failure.parameters,
            )
        }
    }
}
