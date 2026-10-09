package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal

/** Composes pure payroll policies; the caller owns authorization, evidence and transactions. */
fun calculateMonthlyPayroll(facts: PayrollCalculationFacts): Result<PayrollMonthlyCalculation> {
    val assessed = assessPayrollPayUnits(facts)
    if (assessed is Result.Failed) return assessed
    val units = (assessed as Result.Success).value
    val terms = facts.compensation
    val basis = requireNotNull(terms.payBasis)
    val basic = prorateMonthlyPay(terms.basicSalary, units.total, units.payable, basis.rounding)
    if (basic is Result.Failed) return basic
    val basicPay = (basic as Result.Success).value
    val earnings =
        mutableListOf(
            PayrollEarningLine(
                PayrollEarningKind.BASIC,
                "BASIC",
                null,
                basicPay.amount,
                true,
                basicPay,
            )
        )
    for (line in terms.fixedEarnings.sortedBy { it.code }) {
        val prorated = prorateMonthlyPay(line.amount, units.total, units.payable, basis.rounding)
        if (prorated is Result.Failed) return prorated
        val value = (prorated as Result.Success).value
        earnings.add(
            PayrollEarningLine(
                PayrollEarningKind.FIXED,
                line.code,
                line.name,
                value.amount,
                line.taxable,
                value,
            )
        )
    }
    earnings.addAll(
        facts.input.earnings
            .sortedBy { it.code }
            .map {
                PayrollEarningLine(
                    PayrollEarningKind.VARIABLE,
                    it.code,
                    it.name,
                    it.amount,
                    it.taxable,
                )
            }
    )
    val overtimeResult = calculatePayrollOvertime(facts)
    if (overtimeResult is Result.Failed) return overtimeResult
    val overtime = (overtimeResult as Result.Success).value
    if (overtime.isNotEmpty())
        earnings.add(
            PayrollEarningLine(
                PayrollEarningKind.OVERTIME,
                "OVERTIME",
                null,
                overtime.fold(BigDecimal.ZERO) { sum, day -> sum + day.calculation.amount },
                true,
            )
        )
    val holidayResult = calculatePayrollHoliday(facts)
    if (holidayResult is Result.Failed) return holidayResult
    val holiday = (holidayResult as Result.Success).value
    if (holiday != null)
        earnings.add(
            PayrollEarningLine(PayrollEarningKind.HOLIDAY, "HOLIDAY", null, holiday.amount, true)
        )
    val contributionResult = calculateInsuranceContributions(facts.policy, terms)
    if (contributionResult is Result.Failed) return contributionResult
    val contributions = (contributionResult as Result.Success).value
    val taxInputResult = assemblePayrollTaxInput(facts, earnings, contributions)
    if (taxInputResult is Result.Failed) return taxInputResult
    val taxInput = (taxInputResult as Result.Success).value
    val taxResult = calculateIncomeTax(facts.policy.incomeTaxRuleId, taxInput)
    if (taxResult is Result.Failed) return taxResult
    val tax = (taxResult as Result.Success).value
    if (tax.takeHome.signum() < 0)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_negative_take_home"))
    val employeeDeductions =
        taxInput.retirementContributions + taxInput.qualifiedDonations + taxInput.otherNetDeductions
    val cost =
        taxInput.cashEarnings +
            taxInput.nonCashTaxable +
            tax.taxAllowance +
            tax.deductionAllowance +
            contributions
                .filter { it.program in setOf(InsuranceProgram.OLD_AGE, InsuranceProgram.PENSION) }
                .fold(BigDecimal.ZERO) { sum, item -> sum + item.employerAmount }
    if (cost > PAYROLL_MAXIMUM_MONTHLY_AMOUNT)
        return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_amount_limit"))
    return Result.Success(
        PayrollMonthlyCalculation(
            units,
            earnings.toList(),
            overtime,
            holiday,
            contributions,
            taxInput,
            tax,
            employeeDeductions,
            cost,
        )
    )
}
