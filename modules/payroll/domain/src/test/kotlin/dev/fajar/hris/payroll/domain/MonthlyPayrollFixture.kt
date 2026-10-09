package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.people.domain.entities.*
import java.math.BigDecimal
import java.time.*
import org.junit.jupiter.api.Assertions.*

abstract class MonthlyPayrollFixture {
    protected fun amount(value: String) = BigDecimal(value)

    protected fun money(expected: String, actual: BigDecimal) =
        assertEquals(0, amount(expected).compareTo(actual), "Expected $expected; received $actual")

    protected fun facts(
        month: YearMonth = YearMonth.of(2026, 9),
        salary: String = "3000000",
    ): PayrollCalculationFacts {
        val basis =
            PayrollPayBasis(
                INDONESIAN_OVERTIME_PP35_V1,
                INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1,
                PayrollProrationBasis.CALENDAR_DAYS,
                PayrollWorkWeek.FIVE_DAYS,
                null,
                OvertimeEligibility.ELIGIBLE,
                null,
                BigDecimal.ZERO,
                ServiceMonthConvention.CALENDAR_FRACTION,
                EarningsRounding.HALF_UP,
                "Reviewed monthly fixture",
            )
        val compensation =
            CompensationTerms(
                amount(salary),
                emptyList(),
                TaxTreatment.GROSS,
                TaxRegistration(
                    TaxResidency.RESIDENT,
                    PtkpStatus.TK0,
                    "ID",
                    null,
                    null,
                    month.atDay(1),
                    "Reviewed tax fixture",
                ),
                amount(salary),
                emptySet(),
                "Fictional calculator fixture; no enrolment",
                AccidentRisk.VERY_LOW,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                basis,
            )
        return PayrollCalculationFacts(
            month,
            month.atEndOfMonth(),
            month.plusMonths(1).atDay(1),
            PayrollPolicy(
                0,
                0,
                YearMonth.of(month.year, 1),
                YearMonth.of(month.year, 12),
                INDONESIAN_INCOME_TAX_2024,
                INDONESIAN_INSURANCE_PU_V1,
                amount("4000000"),
                amount("12000000"),
                amount("10000000"),
                ContributionRounding.HALF_UP,
                listOf("https://example.test/fictional-reviewed-caps"),
            ),
            compensation,
            PayrollInputTerms(
                emptyList(),
                emptyList(),
                BigDecimal.ZERO,
                null,
                emptyList(),
                null,
                "Reviewed input",
            ),
            listOf(
                EmploymentTerms(
                    LocalDate.of(2024, 1, 1),
                    ContractKind.PERMANENT,
                    LocalDate.of(2024, 1, 1),
                    null,
                    EmploymentStatus.ACTIVE,
                    null,
                    null,
                    null,
                    null,
                    null,
                )
            ),
            (1..month.lengthOfMonth()).map { day ->
                val date = month.atDay(day)
                val works = date.dayOfWeek.value <= 5
                PayrollWorkDay(
                    date,
                    if (works) PayrollScheduleKind.WORK else PayrollScheduleKind.OFF,
                    if (works) PayrollAttendanceKind.WORKED else PayrollAttendanceKind.OFF,
                    false,
                    emptyList(),
                )
            },
            emptyList(),
            PayrollTaxOpeningTerms(
                month.monthValue - 1,
                TaxResidency.RESIDENT,
                PtkpStatus.TK0,
                IncomeTaxHistory(),
                "Explicit prior history fixture",
            ),
        )
    }

    protected fun calculate(facts: PayrollCalculationFacts): PayrollMonthlyCalculation {
        val result = calculateMonthlyPayroll(facts)
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    protected fun failure(facts: PayrollCalculationFacts, code: String): Failure {
        val result = calculateMonthlyPayroll(facts)
        assertTrue(result is Result.Failed, result.toString())
        assertEquals(code, (result as Result.Failed).failure.code)
        return result.failure
    }
}
