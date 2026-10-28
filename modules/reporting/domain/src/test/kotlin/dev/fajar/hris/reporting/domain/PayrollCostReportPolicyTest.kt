package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollCostReportPolicyTest {
    @Test
    fun companyPayrollAndReportGrantsAreBothRequired() {
        val actor =
            Actor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                setOf("reports.read", "payroll.read"),
                Instant.now(),
                UUID.randomUUID(),
            )
        assertEquals(Result.Success(Unit), validatePayrollCostReportAccess(actor))
        for (permissions in
            listOf(
                emptySet(),
                setOf("reports.read"),
                setOf("payroll.read"),
                setOf(
                    "reports.read",
                    "payroll.self.read",
                    "payroll.policy.manage",
                    "payroll.compensation.manage",
                    "payroll.amend",
                    "payroll.pay",
                ),
            )) assertEquals(
            "access_denied",
            (validatePayrollCostReportAccess(actor.copy(permissions = permissions))
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun annualMonthBoundsUsePublishedPayrollYearsAndPermitCrossYearWindows() {
        for ((from, until) in
            listOf(
                "2024-01" to "2024-12",
                "2026-08" to "2027-07",
                "2100-12" to "2100-12",
            )) assertEquals(
            Result.Success(Unit),
            validatePayrollCostReportRange(YearMonth.parse(from), YearMonth.parse(until)),
        )
        for ((from, until) in
            listOf(
                "2023-12" to "2024-01",
                "2100-12" to "2101-01",
                "2026-01" to "2027-01",
                "2026-02" to "2026-01",
            )) {
            val failed =
                validatePayrollCostReportRange(YearMonth.parse(from), YearMonth.parse(until))
                    as Result.Failed
            assertEquals("invalid_report_month_range", failed.failure.code)
            assertEquals(
                mapOf("maximumMonths" to "12", "minimumYear" to "2024", "maximumYear" to "2100"),
                failed.failure.parameters,
            )
        }
    }
}
