package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveStatus
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveReportPolicyTest {
    private val company = UUID.randomUUID()
    private val from = LocalDate.parse("2024-01-01")
    private val until = LocalDate.parse("2024-12-31")
    private val entry =
        LeaveReportRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "E1",
            "Example",
            "AL",
            "Annual leave",
            LeaveStatus.PENDING,
            Instant.parse("2023-12-01T12:34:56.123456Z"),
            0,
            1,
            2,
        )

    @Test
    fun fullLeapYearsAreBoundedAndReversedOrUnsupportedDatesFail() {
        for ((a, b) in
            listOf(
                from to until,
                from to from,
                LocalDate.parse("2200-01-01") to LocalDate.parse("2200-12-31"),
            )) assertEquals(Result.Success(Unit), validateLeaveReportRange(a, b))
        for ((a, b) in
            listOf(
                from to until.plusDays(1),
                from to from.minusDays(1),
                LocalDate.MIN to LocalDate.MAX,
                LocalDate.parse("1899-12-31") to from,
                until to LocalDate.parse("2201-01-01"),
            )) {
            val error = validateLeaveReportRange(a, b) as Result.Failed
            assertEquals("invalid_report_range", error.failure.code)
            assertEquals("366", error.failure.parameters["maximumDays"])
        }
    }

    @Test
    fun reportAndCompanySourceGrantsAreRequiredTogether() {
        val actor = Actor(UUID.randomUUID(), company, emptySet(), Instant.now(), UUID.randomUUID())
        for (grants in
            listOf(
                emptySet(),
                setOf("reports.read"),
                setOf("leave.read"),
                setOf("reports.read", "leave.team.read", "leave.self.manage"),
            )) assertEquals(
            "access_denied",
            (validateLeaveReportAccess(actor.copy(permissions = grants)) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            Result.Success(Unit),
            validateLeaveReportAccess(actor.copy(permissions = setOf("reports.read", "leave.read"))),
        )
    }

    @Test
    fun continuationsRoundTripTheirOrderingKeyWithoutRequiringTheEntryToRemainInTheResult() {
        for (status in listOf(null) + LeaveStatus.entries) {
            val cursor = leaveReportCursor(company, from, until, status, entry)
            assertEquals(
                Result.Success(LeaveReportCursor(entry.submittedAt, entry.id)),
                parseLeaveReportCursor(company, from, until, status, cursor),
            )
        }
        assertEquals(Result.Success(null), parseLeaveReportCursor(company, from, until, null, null))
    }

    @Test
    fun malformedOrReusedContinuationsCannotChangeCompanyWindowOrStatus() {
        val cursor = leaveReportCursor(company, from, until, LeaveStatus.PENDING, entry)
        for (value in
            listOf(
                cursor.replace(company.toString(), UUID.randomUUID().toString()),
                cursor.replace(from.toString(), "2024-01-02"),
                cursor.replace("PENDING", "APPROVED"),
                cursor.replace(entry.id.toString(), "1-1-1-1-1"),
                cursor.replace(entry.submittedAt.toString(), "+10000-01-01T00:00:00Z"),
                cursor.replace("Z|", "+00:00|"),
                cursor + "|extra",
                "x".repeat(201),
                "",
            )) {
            assertEquals(
                "invalid_page",
                (parseLeaveReportCursor(company, from, until, LeaveStatus.PENDING, value)
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
    }
}
