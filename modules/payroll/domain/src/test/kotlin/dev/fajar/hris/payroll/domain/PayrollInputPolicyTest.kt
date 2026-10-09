package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollInputPolicyTest {
    private val month = YearMonth.of(2026, 9)
    private val empty =
        PayrollInputTerms(
            emptyList(),
            emptyList(),
            BigDecimal.ZERO,
            null,
            emptyList(),
            null,
            "Reviewed zero additional inputs",
        )

    @Test
    fun explicitEmptyInputsAndReviewedHalfDaysAreValidWithoutInventingAnAbsencePolicy() {
        assertEquals(Result.Success(Unit), validatePayrollInput(month, empty, "Reviewed"))
        val first =
            PayrollDayResolution(
                LocalDate.of(2026, 9, 1),
                PayrollDayPortion.FIRST_HALF,
                PayrollDayDisposition.PAID,
                "Reviewed paid absence",
            )
        val second =
            first.copy(
                portion = PayrollDayPortion.SECOND_HALF,
                disposition = PayrollDayDisposition.UNPAID,
            )
        val selected =
            empty.copy(
                scheduledMonthUnits = BigDecimal("21.5"),
                dayResolutions = listOf(first, second),
            )
        assertEquals(Result.Success(Unit), validatePayrollInput(month, selected, "Reviewed"))
        assertEquals(
            "invalid_payroll_input",
            (validatePayrollInput(
                    month,
                    selected.copy(dayResolutions = listOf(first, first)),
                    "Reviewed",
                )
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun inputBoundsRejectHugeNumbersOverlappingDatesAndUnboundedCollections() {
        val earning = PayrollVariableEarning("BONUS", "Monthly bonus", BigDecimal.ONE, true)
        for (terms in
            listOf(
                empty.copy(nonCashTaxable = BigDecimal("1E+1000000")),
                empty.copy(scheduledMonthUnits = BigDecimal("1E-1000000")),
                empty.copy(earnings = List(41) { earning }),
                empty.copy(earnings = listOf(earning, earning)),
                empty.copy(
                    deductions = listOf(PayrollNetDeduction("ADVANCE", "Advance", BigDecimal("-1")))
                ),
                empty.copy(
                    dayResolutions =
                        List(63) {
                            PayrollDayResolution(
                                LocalDate.of(2026, 9, 1),
                                PayrollDayPortion.FULL,
                                PayrollDayDisposition.PAID,
                                "Reviewed",
                            )
                        }
                ),
            )) {
            assertTrue(validatePayrollInput(month, terms, "Reviewed") is Result.Failed)
        }
        val holiday =
            PayrollHolidayInput(
                PayrollHolidayKind.CHRISTMAS,
                LocalDate.of(2026, 12, 25),
                LocalDate.of(2025, 1, 1),
                PayrollPriorHolidayPayment.NOT_PAID,
                "Verified employment and previous payment",
                null,
            )
        assertTrue(
            validatePayrollInput(
                YearMonth.of(-999999999, 1),
                empty.copy(holidayAllowance = holiday),
                "Reviewed",
            )
                is Result.Failed
        )
    }

    @Test
    fun inputFingerprintsNormalizeMoneyAndOrderingButPreservePaymentEvidence() {
        val a = PayrollVariableEarning("BONUS", "Bonus", BigDecimal("1000"), true)
        val b = PayrollVariableEarning("MEAL", "Meal", BigDecimal("2000"), false)
        val original = empty.copy(earnings = listOf(a, b))
        val reordered = empty.copy(earnings = listOf(b, a.copy(amount = BigDecimal("1000.00"))))
        assertEquals(payrollInputParts(original), payrollInputParts(reordered))
        assertNotEquals(
            payrollInputParts(original),
            payrollInputParts(original.copy(nonCashTaxable = BigDecimal.ONE)),
        )
    }

    @Test
    fun thePaymentMonthIsDistinctFromTheEarningsMonth() {
        val id = UUID.randomUUID()
        val period =
            PayrollPeriod(
                id,
                YearMonth.of(2026, 12),
                LocalDate.of(2027, 1, 2),
                "Asia/Jakarta",
                1,
                id,
                Instant.EPOCH,
                PayrollPeriodStatus.DRAFT,
                0,
            )
        assertEquals(YearMonth.of(2027, 1), period.taxMonth)
        assertEquals(
            Result.Success(Unit),
            validatePayrollPeriod(
                period.earningsMonth,
                period.plannedPaymentDate,
                setOf(id),
                "Reviewed",
            ),
        )
        assertTrue(
            validatePayrollPeriod(
                period.earningsMonth,
                LocalDate.of(2026, 11, 30),
                setOf(id),
                "Reviewed",
            )
                is Result.Failed
        )
    }

    @Test
    fun payrollRosterAndPaymentRangesAreFiniteBeforeRepositoryCalls() {
        val roster = (1..5000).map { UUID(0, it.toLong()) }.toSet()
        assertEquals(
            Result.Success(Unit),
            validatePayrollPeriod(month, LocalDate.of(2026, 10, 2), roster, "Reviewed"),
        )
        assertTrue(
            validatePayrollPeriod(month, LocalDate.of(2026, 10, 2), roster + UUID(1, 1), "Reviewed")
                is Result.Failed
        )
        assertTrue(
            validatePayrollPeriod(month, LocalDate.of(2027, 1, 1), roster, "Reviewed")
                is Result.Failed
        )
        assertTrue(
            validatePayrollPeriod(
                YearMonth.of(-999999999, 1),
                LocalDate.of(2026, 10, 2),
                roster,
                "Reviewed",
            )
                is Result.Failed
        )
    }
}
