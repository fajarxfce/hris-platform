package dev.fajar.hris.leave.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveAccrualFrequency
import dev.fajar.hris.leave.domain.policies.*
import java.time.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveAccrualSchedulePolicyTest {
    @Test
    fun monthlyCreditWaitsForTheWholeMonthIncludingLeapDay() {
        assertEquals(
            Instant.parse("2028-02-29T17:00:00Z"),
            leaveAccrualDueAt(YearMonth.of(2028, 2), LeaveAccrualFrequency.MONTHLY, "Asia/Jakarta"),
        )
        assertEquals(
            Instant.parse("2027-02-28T17:00:00Z"),
            leaveAccrualDueAt(YearMonth.of(2027, 2), LeaveAccrualFrequency.MONTHLY, "Asia/Jakarta"),
        )
    }

    @Test
    fun annualPlanKeepsTheSelectedEligibilityMonthAndDistinctYears() {
        val months =
            leaveAccrualScheduleMonths(YearMonth.of(2027, 4), 3, LeaveAccrualFrequency.ANNUAL)
        assertEquals(
            Result.Success(
                listOf(YearMonth.of(2027, 4), YearMonth.of(2028, 4), YearMonth.of(2029, 4))
            ),
            months,
        )
        assertEquals(
            Instant.parse("2027-03-31T17:00:00Z"),
            leaveAccrualDueAt(YearMonth.of(2027, 4), LeaveAccrualFrequency.ANNUAL, "Asia/Jakarta"),
        )
    }

    @Test
    fun monthBoundariesUseTheCompanyZoneRatherThanAServerOffset() {
        assertEquals(
            Instant.parse("2026-03-01T05:00:00Z"),
            leaveAccrualDueAt(
                YearMonth.of(2026, 2),
                LeaveAccrualFrequency.MONTHLY,
                "America/New_York",
            ),
        )
        assertEquals(
            Instant.parse("2026-04-01T04:00:00Z"),
            leaveAccrualDueAt(
                YearMonth.of(2026, 3),
                LeaveAccrualFrequency.MONTHLY,
                "America/New_York",
            ),
        )
    }

    @Test
    fun repeatedMidnightUsesTheSameLaterOffsetAsPostgres() {
        assertEquals(
            Instant.parse("2026-11-01T05:00:00Z"),
            leaveAccrualDueAt(
                YearMonth.of(2026, 10),
                LeaveAccrualFrequency.MONTHLY,
                "America/Havana",
            ),
        )
    }

    @Test
    fun plansRejectUnboundedCountsUnsupportedPolicyAndTheStorageYearLimit() {
        for (count in listOf(Int.MIN_VALUE, 0, 13, Int.MAX_VALUE)) assertTrue(
            leaveAccrualScheduleMonths(YearMonth.of(2026, 1), count, LeaveAccrualFrequency.MONTHLY)
                is Result.Failed
        )
        assertTrue(
            leaveAccrualScheduleMonths(YearMonth.of(2199, 12), 2, LeaveAccrualFrequency.MONTHLY)
                is Result.Failed
        )
        assertTrue(
            leaveAccrualScheduleMonths(YearMonth.of(2199, 1), 2, LeaveAccrualFrequency.ANNUAL)
                is Result.Failed
        )
        val manual =
            leaveAccrualScheduleMonths(YearMonth.of(2026, 1), 1, LeaveAccrualFrequency.MANUAL)
        assertEquals("leave_accrual_not_configured", (manual as Result.Failed).failure.code)
    }
}
