package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.math.BigDecimal
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimePayPolicyTest {
    private fun pay(
        kind: OvertimeDayKind,
        minutes: Int,
        basic: String = "1730000",
        nonFixed: String = "0",
        rounding: EarningsRounding = EarningsRounding.HALF_UP,
    ): OvertimePay {
        val result =
            calculateOvertimePay(
                INDONESIAN_OVERTIME_PP35_V1,
                BigDecimal(basic),
                BigDecimal.ZERO,
                BigDecimal(nonFixed),
                kind,
                minutes,
                rounding,
            )
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    @Test
    fun pp35WeekdayAndBothHolidayWorkweeksApplyTheirExactHourlyBands() {
        assertEquals(BigDecimal("15000"), pay(OvertimeDayKind.WORKDAY, 60).amount)
        assertEquals(BigDecimal("25000"), pay(OvertimeDayKind.WORKDAY, 90).amount)
        assertEquals(BigDecimal("75000"), pay(OvertimeDayKind.WORKDAY, 240).amount)
        for ((kind, minutes, expected) in
            listOf(
                Triple(OvertimeDayKind.REST_OR_HOLIDAY_FIVE_DAYS, 720, "310000"),
                Triple(OvertimeDayKind.REST_OR_HOLIDAY_SIX_DAYS, 660, "290000"),
                Triple(OvertimeDayKind.HOLIDAY_SHORT_SIX_DAYS, 540, "250000"),
            )) {
            val result = pay(kind, minutes)
            assertEquals(BigDecimal(expected), result.amount)
            assertEquals(minutes, result.segments.sumOf { it.minutes })
            assertEquals(3, result.segments.size)
            assertEquals(INDONESIAN_OVERTIME_PP35_V1, result.ruleId)
        }
    }

    @Test
    fun regularNonFixedWagesRaiseTheBaseToSeventyFivePercentWhenRequired() {
        val raised = pay(OvertimeDayKind.WORKDAY, 120, "1000000", "3000000")
        assertEquals(0, BigDecimal("3000000").compareTo(raised.monthlyWage))
        assertEquals(BigDecimal("60694"), raised.amount)
        assertEquals(
            BigDecimal("15000"),
            pay(OvertimeDayKind.WORKDAY, 60, nonFixed = "100000").amount,
        )
    }

    @Test
    fun fractionalHoursRoundOnlyTheFinalAmountAndUnsupportedTimeRequiresReview() {
        assertEquals(BigDecimal("5"), pay(OvertimeDayKind.WORKDAY, 120, "252").amount)
        assertEquals(BigDecimal("0"), pay(OvertimeDayKind.WORKDAY, 1, "1000").amount)
        assertEquals(
            BigDecimal("1"),
            pay(OvertimeDayKind.WORKDAY, 1, "1000", rounding = EarningsRounding.UP).amount,
        )
        val denied =
            calculateOvertimePay(
                INDONESIAN_OVERTIME_PP35_V1,
                BigDecimal("1730000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OvertimeDayKind.WORKDAY,
                241,
                EarningsRounding.HALF_UP,
            )
        assertEquals("payroll_overtime_review_required", (denied as Result.Failed).failure.code)
        assertEquals("241", denied.failure.parameters["minutes"])
        assertTrue(
            calculateOvertimePay(
                INDONESIAN_OVERTIME_PP35_V1,
                BigDecimal("1E+1000000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OvertimeDayKind.WORKDAY,
                60,
                EarningsRounding.HALF_UP,
            )
                is Result.Failed
        )
    }

    @Test
    fun anInterruptedCalculationNeverProducesAPayableResult() {
        Executors.newSingleThreadExecutor().use { pool ->
            val future =
                pool.submit<Result<OvertimePay>> {
                    Thread.currentThread().interrupt()
                    calculateOvertimePay(
                        INDONESIAN_OVERTIME_PP35_V1,
                        BigDecimal("1730000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        OvertimeDayKind.WORKDAY,
                        60,
                        EarningsRounding.HALF_UP,
                    )
                }
            val failure =
                assertThrows(ExecutionException::class.java) { future.get(5, TimeUnit.SECONDS) }
            assertTrue(failure.cause is InterruptedException)
        }
    }
}
