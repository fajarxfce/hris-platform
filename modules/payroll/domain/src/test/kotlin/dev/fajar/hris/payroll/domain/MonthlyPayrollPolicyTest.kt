package dev.fajar.hris.payroll.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.people.domain.entities.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MonthlyPayrollPolicyTest : MonthlyPayrollFixture() {
    @Test
    fun `monthly composition classifies employer insurance and employee deductions separately`() {
        val base = facts(salary = "10000000")
        val result =
            calculate(
                base.copy(
                    compensation =
                        base.compensation.copy(
                            insurancePrograms = InsuranceProgram.entries.toSet(),
                            insuranceExemptionReason = null,
                        )
                )
            )
        money("10000000", result.taxInput.cashEarnings)
        money("454000", result.taxInput.nonCashTaxable)
        money("300000", result.taxInput.retirementContributions)
        money("100000", result.taxInput.otherNetDeductions)
        money("10454000", result.tax.taxableGross)
        money("261350", result.tax.withheld)
        money("9338650", result.tax.takeHome)
        money("400000", result.employeeDeductions)
        money("11024000", result.employerCost)
        assertEquals(base.month, result.taxInput.month)
    }

    @Test
    fun `midmonth hiring prorates fixed pay but preserves declared variable earnings`() {
        val base = facts()
        val result =
            calculate(
                base.copy(
                    employment =
                        listOf(
                            base.employment
                                .single()
                                .copy(
                                    effectiveFrom = base.month.atDay(16),
                                    startDate = base.month.atDay(16),
                                )
                        ),
                    input =
                        base.input.copy(
                            earnings =
                                listOf(
                                    PayrollVariableEarning("BONUS", "Bonus", amount("500000"), true)
                                ),
                            deductions =
                                listOf(
                                    PayrollNetDeduction(
                                        "ADVANCE",
                                        "Advance repayment",
                                        amount("50000"),
                                    )
                                ),
                        ),
                )
            )
        money("15", result.units.payable)
        money("1500000", result.earnings.first().amount)
        money("2000000", result.taxInput.cashEarnings)
        money("1950000", result.tax.takeHome)
        assertEquals(2, result.earnings.size)
    }

    @Test
    fun `effective employment ending midmonth keeps earlier earned days and reconciles tax`() {
        val base = facts()
        val ended =
            base.employment
                .single()
                .copy(
                    effectiveFrom = base.month.atDay(16),
                    status = EmploymentStatus.ENDED,
                    endDate = base.month.atDay(15),
                )
        val result = calculate(base.copy(employment = base.employment + ended))
        money("15", result.units.payable)
        money("1500000", result.taxInput.cashEarnings)
        assertTrue(result.taxInput.finalPeriod)
        assertEquals(12, result.taxInput.subjectiveMonths)
    }

    @Test
    fun `nonfixed wage is an overtime reference and is not added twice to cash`() {
        val base = facts(salary = "1730000")
        val first = base.workDays.first()
        val value =
            base.copy(
                compensation =
                    base.compensation.copy(
                        payBasis =
                            base.compensation.payBasis!!.copy(
                                regularNonFixedWage = amount("100000")
                            )
                    ),
                input =
                    base.input.copy(
                        earnings =
                            listOf(
                                PayrollVariableEarning(
                                    "ALLOWANCE",
                                    "Allowance",
                                    amount("100000"),
                                    true,
                                )
                            )
                    ),
                workDays =
                    base.workDays.map {
                        if (it == first)
                            it.copy(
                                overtime =
                                    listOf(
                                        PayrollOvertimeEvidence(UUID.randomUUID(), 1, 30),
                                        PayrollOvertimeEvidence(UUID.randomUUID(), 1, 30),
                                    )
                            )
                        else it
                    },
            )
        val result = calculate(value)
        money("15000", result.overtime.single().calculation.amount)
        money("1845000", result.taxInput.cashEarnings)
        assertEquals(1, result.overtime.single().calculation.segments.size)
        val exempt =
            calculate(
                value.copy(
                    compensation =
                        value.compensation.copy(
                            payBasis =
                                value.compensation.payBasis!!.copy(
                                    overtimeEligibility = OvertimeEligibility.EXEMPT,
                                    overtimeExemptionReference = "Reviewed exemption",
                                )
                        )
                )
            )
        assertTrue(exempt.overtime.isEmpty())
        money("1830000", exempt.taxInput.cashEarnings)
    }

    @Test
    fun `a dated holiday adds full THR once and prior payment does not add it again`() {
        val base = facts()
        val holiday =
            PayrollHolidayInput(
                PayrollHolidayKind.EID_FITR,
                base.month.atDay(25),
                LocalDate.of(2024, 1, 1),
                PayrollPriorHolidayPayment.NOT_PAID,
                "Fictional holiday declaration",
                null,
            )
        val value = base.copy(input = base.input.copy(holidayAllowance = holiday))
        val result = calculate(value)
        money("3000000", requireNotNull(result.holiday).amount)
        assertEquals(base.month.atDay(18), requireNotNull(result.holiday).dueDate)
        money("6000000", result.taxInput.cashEarnings)
        money("45000", result.tax.withheld)
        money(
            "3000000",
            calculate(
                    value.copy(
                        input =
                            value.input.copy(
                                holidayAllowance =
                                    holiday.copy(priorPayment = PayrollPriorHolidayPayment.PAID)
                            )
                    )
                )
                .taxInput
                .cashEarnings,
        )
        failure(
            value.copy(
                input =
                    value.input.copy(
                        holidayAllowance =
                            holiday.copy(holidayDate = base.month.plusMonths(1).atDay(15))
                    )
            ),
            "payroll_thr_tax_month_mismatch",
        )
    }

    @Test
    fun `the assembled year end reproduces PMK168 Tuan B refund with September hiring`() {
        val base = facts(YearMonth.of(2024, 12), "15500000")
        val result =
            calculate(
                base.copy(
                    compensation =
                        base.compensation.copy(additionalRetirementContribution = amount("100000")),
                    employment =
                        listOf(
                            base.employment
                                .single()
                                .copy(
                                    effectiveFrom = LocalDate.of(2024, 9, 1),
                                    startDate = LocalDate.of(2024, 9, 1),
                                )
                        ),
                    taxHistory =
                        base.taxHistory.copy(
                            history =
                                IncomeTaxHistory(
                                    taxableGross = amount("46500000"),
                                    retirementContributions = amount("300000"),
                                    withheld = amount("3255000"),
                                    employmentMonths = 3,
                                )
                        ),
                )
            )
        assertTrue(result.taxInput.finalPeriod)
        assertEquals(12, result.taxInput.subjectiveMonths)
        money("280000", requireNotNull(result.tax.annualTax))
        money("-2975000", result.tax.withheld)
        money("18375000", result.tax.takeHome)
    }

    @Test
    fun `nonresident and gross up treatment remain independent of contract type`() {
        val base = facts(salary = "10000000")
        val foreign =
            base.copy(
                compensation =
                    base.compensation.copy(
                        tax =
                            base.compensation.tax.copy(
                                residency = TaxResidency.NON_RESIDENT,
                                residenceCountry = "AU",
                            )
                    ),
                taxHistory = base.taxHistory.copy(residency = TaxResidency.NON_RESIDENT),
            )
        money("2000000", calculate(foreign).tax.withheld)
        money("8000000", calculate(foreign).tax.takeHome)
        val grossUp =
            calculate(
                foreign.copy(
                    compensation = foreign.compensation.copy(treatment = TaxTreatment.GROSS_UP)
                )
            )
        money("10000000", grossUp.tax.takeHome)
        assertEquals(0, grossUp.tax.taxAllowance.compareTo(grossUp.tax.withheld))
    }

    @Test
    fun `missing history changed residency and a later liability require explicit review`() {
        val base = facts()
        failure(
            base.copy(taxHistory = base.taxHistory.copy(throughMonth = 7)),
            "payroll_tax_history_incomplete",
        )
        failure(
            base.copy(taxHistory = base.taxHistory.copy(ptkp = PtkpStatus.K0)),
            "payroll_tax_history_mismatch",
        )
        failure(
            base.copy(incomeDueDate = base.month.plusMonths(1).atDay(1)),
            "payroll_tax_month_review_required",
        )
        failure(
            base.copy(compensation = base.compensation.copy(payBasis = null)),
            "payroll_pay_basis_required",
        )
    }

    @Test
    fun `net deductions and exemption classifications are visible in exact tax input`() {
        val base = facts(salary = "10000000")
        val value =
            base.copy(
                compensation =
                    base.compensation.copy(
                        treatment = TaxTreatment.NET,
                        fixedEarnings =
                            listOf(
                                FixedEarning("EXEMPT", "Exempt benefit", amount("1000000"), false)
                            ),
                        additionalRetirementContribution = amount("100000"),
                    ),
                input =
                    base.input.copy(
                        deductions =
                            listOf(PayrollNetDeduction("LOAN", "Loan repayment", amount("200000")))
                    ),
            )
        val result = calculate(value)
        money("11000000", result.taxInput.cashEarnings)
        money("1000000", result.taxInput.nonTaxableCash)
        money("300000", result.employeeDeductions)
        money("300000", result.tax.deductionAllowance)
        money("11000000", result.tax.takeHome)
    }

    @Test
    fun `bounds and interruption stop before a payable result is returned`() {
        val base = facts()
        failure(base.copy(workDays = base.workDays.dropLast(1)), "payroll_source_incomplete")
        failure(
            base.copy(workDays = base.workDays.dropLast(1) + base.workDays.first()),
            "payroll_source_incomplete",
        )
        failure(
            base.copy(employment = List(129) { base.employment.single() }),
            "payroll_source_incomplete",
        )
        assertTrue(
            calculateMonthlyPayroll(
                base.copy(
                    compensation = base.compensation.copy(basicSalary = BigDecimal("1E+999999"))
                )
            )
                is Result.Failed
        )
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) { calculateMonthlyPayroll(base) }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `unfunded taxable benefits never produce a negative payment instruction`() {
        val base = facts(salary = "1000000")
        failure(
            base.copy(input = base.input.copy(nonCashTaxable = amount("100000000"))),
            "payroll_negative_take_home",
        )
    }
}
