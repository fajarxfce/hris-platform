package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.prorateMonthlyPay
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayProrationPolicyTest {
    @Test
    fun explicitCalendarOrScheduledUnitsPreserveHalfDaysAndFinalRounding() {
        val calendar =
            prorateMonthlyPay(
                BigDecimal("3100000"),
                BigDecimal(31),
                BigDecimal(16),
                EarningsRounding.HALF_UP,
            )
                as Result.Success
        assertEquals(BigDecimal("1600000"), calendar.value.amount)
        val leap =
            prorateMonthlyPay(
                BigDecimal("2900000"),
                BigDecimal(29),
                BigDecimal("14.5"),
                EarningsRounding.HALF_UP,
            )
                as Result.Success
        assertEquals(BigDecimal("1450000"), leap.value.amount)
        val work =
            prorateMonthlyPay(
                BigDecimal("4093"),
                BigDecimal(3),
                BigDecimal.ONE,
                EarningsRounding.UP,
            )
                as Result.Success
        assertEquals(BigDecimal("1365"), work.value.amount)
        val none =
            prorateMonthlyPay(
                BigDecimal("10000000"),
                BigDecimal(22),
                BigDecimal.ZERO,
                EarningsRounding.HALF_UP,
            )
                as Result.Success
        assertEquals(BigDecimal.ZERO, none.value.amount)
    }

    @Test
    fun invalidOrUnboundedUnitsCannotBecomeSilentZeroPay() {
        for ((total, payable) in
            listOf(
                "0" to "0",
                "32" to "1",
                "1" to "-1",
                "1" to "2",
                "1E+1000000" to "1",
                "1" to "0.0001",
            )) {
            val result =
                prorateMonthlyPay(
                    BigDecimal("10000000"),
                    BigDecimal(total),
                    BigDecimal(payable),
                    EarningsRounding.HALF_UP,
                )
            assertEquals("invalid_payroll_proration", (result as Result.Failed).failure.code)
        }
    }
}
