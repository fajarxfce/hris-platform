package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimeReportPolicyTest {
    @Test
    fun reportAdmissionRetainsCanonicalCompanyOvertimeGrantsWithoutExpandingSelfOrTeamScope() {
        val actor =
            Actor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                emptySet(),
                Instant.now(),
                UUID.randomUUID(),
            )
        for (source in listOf("overtime.read", "overtime.manage", "workforce.read")) {
            assertEquals(
                Result.Success(Unit),
                validateOvertimeReportAccess(
                    actor.copy(permissions = setOf("reports.read", source))
                ),
            )
            assertEquals(
                "access_denied",
                (validateOvertimeReportAccess(actor.copy(permissions = setOf(source)))
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
        for (sources in
            listOf(
                emptySet(),
                setOf("overtime.self.manage"),
                setOf("overtime.team.read", "workforce.team.read"),
                setOf("overtime.approve", "approvals.read"),
            )) {
            assertEquals(
                "access_denied",
                (validateOvertimeReportAccess(actor.copy(permissions = sources + "reports.read"))
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
    }

    @Test
    fun annualLeapWindowsHaveExplicitBoundsMatchingStoredOvertimeWorkDates() {
        val first = LocalDate.parse("2024-01-01")
        for ((from, until) in
            listOf(
                first to first,
                first to first.plusDays(365),
                LocalDate.parse("2000-01-01") to LocalDate.parse("2000-12-31"),
                LocalDate.parse("2100-01-01") to LocalDate.parse("2100-12-31"),
            )) assertEquals(Result.Success(Unit), validateOvertimeReportRange(from, until))
        for ((from, until) in
            listOf(
                first to first.plusDays(366),
                first to first.minusDays(1),
                LocalDate.MIN to LocalDate.MAX,
                LocalDate.parse("1999-12-31") to LocalDate.parse("2000-01-01"),
                LocalDate.parse("2100-12-31") to LocalDate.parse("2101-01-01"),
            )) {
            val result = validateOvertimeReportRange(from, until) as Result.Failed
            assertEquals("invalid_report_range", result.failure.code)
            assertEquals(
                mapOf("maximumDays" to "366", "minimumYear" to "2000", "maximumYear" to "2100"),
                result.failure.parameters,
            )
        }
    }
}
