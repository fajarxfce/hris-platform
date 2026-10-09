package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.people.domain.entities.ContractKind
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HolidayAllowancePolicyTest {
    private fun service(
        start: String,
        holiday: String,
        convention: ServiceMonthConvention = ServiceMonthConvention.CALENDAR_FRACTION,
        end: String? = null,
        contract: ContractKind = ContractKind.PERMANENT,
    ): HolidayService {
        val result =
            assessHolidayService(
                INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1,
                LocalDate.parse(start),
                end?.let(LocalDate::parse),
                contract,
                LocalDate.parse(holiday),
                convention,
            )
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    private fun amount(
        service: HolidayService,
        holiday: String,
        promised: String? = null,
    ): HolidayAllowance {
        val result =
            calculateHolidayAllowance(
                INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1,
                service,
                LocalDate.parse(holiday),
                BigDecimal("12000000"),
                promised?.let(::BigDecimal),
                EarningsRounding.HALF_UP,
            )
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    @Test
    fun minimumProportionalAndAnnualAmountsRetainTheHolidayPaymentDeadline() {
        val partial = amount(service("2026-01-01", "2026-07-01"), "2026-07-01")
        assertEquals(BigDecimal("6000000"), partial.amount)
        assertEquals(LocalDate.parse("2026-06-24"), partial.dueDate)
        assertEquals(INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1, partial.ruleId)
        assertEquals(
            BigDecimal("12000000"),
            amount(service("2024-01-01", "2026-01-01"), "2026-01-01").amount,
        )
        assertEquals(
            BigDecimal("1000000"),
            amount(service("2026-01-31", "2026-02-28"), "2026-02-28").amount,
        )
    }

    @Test
    fun reviewedFractionalServicePreservesCalendarAnniversariesWithoutIntermediateRounding() {
        val fraction = service("2026-01-31", "2026-03-15")
        assertEquals(1, fraction.wholeMonths)
        assertEquals(15, fraction.remainingDays)
        assertEquals(31, fraction.anniversaryDays)
        assertEquals(BigDecimal("1483871"), amount(fraction, "2026-03-15").amount)
        val whole = service("2026-01-31", "2026-03-15", ServiceMonthConvention.COMPLETE_MONTHS)
        assertEquals(BigDecimal("1000000"), amount(whole, "2026-03-15").amount)
    }

    @Test
    fun permanentTerminationWindowDoesNotExtendToExpiredFixedTermContractsOrAnotherYear() {
        assertEquals(
            BigDecimal("12000000"),
            amount(service("2024-01-01", "2026-12-31", end = "2026-12-01"), "2026-12-31").amount,
        )
        for ((end, holiday, contract) in
            listOf(
                Triple("2026-12-01", "2026-12-31", ContractKind.FIXED_TERM),
                Triple("2026-11-30", "2026-12-31", ContractKind.PERMANENT),
                Triple("2026-12-20", "2027-01-10", ContractKind.PERMANENT),
            )) {
            val result =
                assessHolidayService(
                    INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1,
                    LocalDate.parse("2024-01-01"),
                    LocalDate.parse(end),
                    contract,
                    LocalDate.parse(holiday),
                    ServiceMonthConvention.COMPLETE_MONTHS,
                )
            assertEquals("payroll_thr_ineligible", (result as Result.Failed).failure.code)
        }
        val short =
            assessHolidayService(
                INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1,
                LocalDate.parse("2026-06-15"),
                null,
                ContractKind.FIXED_TERM,
                LocalDate.parse("2026-07-01"),
                ServiceMonthConvention.CALENDAR_FRACTION,
            )
        assertEquals("payroll_thr_ineligible", (short as Result.Failed).failure.code)
    }

    @Test
    fun moreFavorableEmployerCommitmentsRemainSeparateFromTheStatutoryMinimum() {
        val assessed = service("2026-01-01", "2026-07-01")
        val enhanced = amount(assessed, "2026-07-01", "8000000")
        assertEquals(BigDecimal("6000000"), enhanced.statutoryAmount)
        assertEquals(BigDecimal("2000000"), enhanced.employerTopUp)
        assertEquals(BigDecimal("8000000"), enhanced.amount)
        val reduced =
            calculateHolidayAllowance(
                INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1,
                assessed,
                LocalDate.parse("2026-07-01"),
                BigDecimal("12000000"),
                BigDecimal("5000000"),
                EarningsRounding.HALF_UP,
            )
        assertEquals("payroll_thr_below_minimum", (reduced as Result.Failed).failure.code)
    }

    @Test
    fun inconsistentServiceEvidenceOrAmountsCannotProduceAnAllowance() {
        val assessed = service("2026-01-01", "2026-02-01")
        for (invalid in
            listOf(
                assessed.copy(wholeMonths = 12),
                assessed.copy(remainingDays = 31),
                assessed.copy(anniversaryDays = 0),
            )) {
            val result =
                calculateHolidayAllowance(
                    INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1,
                    invalid,
                    LocalDate.parse("2026-02-01"),
                    BigDecimal("12000000"),
                    null,
                    EarningsRounding.HALF_UP,
                )
            assertTrue(result is Result.Failed, result.toString())
        }
        assertTrue(
            calculateHolidayAllowance(
                INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1,
                assessed,
                LocalDate.parse("2026-02-01"),
                BigDecimal("1E+1000000"),
                null,
                EarningsRounding.HALF_UP,
            )
                is Result.Failed
        )
    }
}
