package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.math.RoundingMode

fun validateIncomeTaxInput(input: IncomeTaxInput): Result<Unit> {
    val history = input.history
    val amounts =
        listOf(
            input.cashEarnings,
            input.nonTaxableCash,
            input.nonCashTaxable,
            input.retirementContributions,
            input.qualifiedDonations,
            input.otherNetDeductions,
        )
    val historical =
        listOf(
            history.taxableGross,
            history.retirementContributions,
            history.qualifiedDonations,
            history.withheld,
            history.previousEmployerNet,
            history.previousEmployerWithheld,
        )
    if (
        input.month.year !in 2000..2100 ||
            input.subjectiveMonths !in 1..12 ||
            history.employmentMonths !in 0..11 ||
            history.employmentMonths >= input.month.monthValue ||
            input.subjectiveMonths < history.employmentMonths + 1 ||
            amounts.any { !validPayrollAmount(it) } ||
            historical.any { !validPayrollAmount(it, yearly = true) } ||
            input.nonTaxableCash > input.cashEarnings ||
            input.retirementContributions + input.qualifiedDonations + input.otherNetDeductions >
                input.cashEarnings ||
            history.retirementContributions + history.qualifiedDonations > history.taxableGross ||
            history.taxableGross >
                PAYROLL_MAXIMUM_MONTHLY_AMOUNT.multiply(BigDecimal(history.employmentMonths)) ||
            (history.employmentMonths == 0 &&
                (history.taxableGross.signum() != 0 ||
                    history.retirementContributions.signum() != 0 ||
                    history.qualifiedDonations.signum() != 0 ||
                    history.withheld.signum() != 0))
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_tax_input"))
    return Result.Success(Unit)
}

/** Pure tax policy. Explicit finite fixed-point iteration handles the jumps between TER bands. */
fun calculateIncomeTax(ruleId: String, input: IncomeTaxInput): Result<IncomeTaxCalculation> {
    val valid = validateIncomeTaxInput(input)
    if (valid is Result.Failed) return valid
    val rules =
        incomeTaxRules(ruleId)
            ?: return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_tax_rule_unavailable"))
    if (
        input.month.atDay(1) < rules.effectiveFrom ||
            (rules.effectiveUntil != null && input.month.atEndOfMonth() > rules.effectiveUntil)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_tax_rule_not_effective"))
    val deductions =
        input.retirementContributions + input.qualifiedDonations + input.otherNetDeductions
    val deductionAllowance =
        if (input.treatment == TaxTreatment.NET) deductions else BigDecimal.ZERO
    var allowance = BigDecimal.ZERO
    repeat(256) {
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException("Payroll calculation interrupted")
        val gross =
            input.cashEarnings - input.nonTaxableCash +
                input.nonCashTaxable +
                deductionAllowance +
                allowance
        if (gross > PAYROLL_MAXIMUM_MONTHLY_AMOUNT)
            return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_amount_limit"))
        val tax = calculateIncomeTaxAtGross(rules, input, gross, allowance, deductionAllowance)
        val required =
            if (input.treatment == TaxTreatment.GROSS) BigDecimal.ZERO
            else tax.withheld.max(BigDecimal.ZERO)
        if (required.compareTo(allowance) == 0) return Result.Success(tax)
        if (required < allowance)
            return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_allowance_unresolved"))
        allowance = required
    }
    return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_allowance_unresolved"))
}

/** Receives validated bounded inputs and a reviewed immutable rule pack. No I/O or workflow. */
fun calculateIncomeTaxAtGross(
    rules: IncomeTaxRules,
    input: IncomeTaxInput,
    gross: BigDecimal,
    taxAllowance: BigDecimal,
    deductionAllowance: BigDecimal,
): IncomeTaxCalculation {
    val deductions =
        input.retirementContributions + input.qualifiedDonations + input.otherNetDeductions
    val category =
        if (input.residency == TaxResidency.RESIDENT) rules.categories.getValue(input.ptkp)
        else null
    val rate =
        when {
            category == null -> rules.nonResidentRate
            !input.finalPeriod ->
                rules.monthly
                    .getValue(category)
                    .first { it.upperInclusive == null || gross <= it.upperInclusive }
                    .rate
            else -> null
        }
    var annualNet: BigDecimal? = null
    var annualized: BigDecimal? = null
    var annualTaxable: BigDecimal? = null
    var annualTax: BigDecimal? = null
    val withholding =
        if (rate != null) gross.multiply(rate).setScale(0, RoundingMode.DOWN)
        else {
            val history = input.history
            val totalGross = history.taxableGross + gross
            val expense =
                totalGross
                    .multiply(rules.jobExpenseRate)
                    .min(
                        rules.monthlyJobExpenseCap.multiply(
                            BigDecimal(history.employmentMonths + 1)
                        )
                    )
            annualNet =
                (totalGross -
                        expense -
                        history.retirementContributions -
                        input.retirementContributions -
                        history.qualifiedDonations -
                        input.qualifiedDonations + history.previousEmployerNet)
                    .max(BigDecimal.ZERO)
            annualized =
                annualNet
                    .multiply(BigDecimal(12))
                    .divide(BigDecimal(input.subjectiveMonths), 10, RoundingMode.DOWN)
            annualTaxable =
                (annualized - rules.allowances.getValue(input.ptkp))
                    .max(BigDecimal.ZERO)
                    .divide(BigDecimal(1000), 0, RoundingMode.DOWN)
                    .multiply(BigDecimal(1000))
            var lower = BigDecimal.ZERO
            var progressive = BigDecimal.ZERO
            for (band in rules.progressive) {
                val upper = band.upperInclusive ?: annualTaxable
                val taxable = annualTaxable.min(upper).subtract(lower).max(BigDecimal.ZERO)
                progressive += taxable.multiply(band.rate)
                lower = upper
            }
            annualTax =
                progressive
                    .multiply(BigDecimal(input.subjectiveMonths))
                    .divide(BigDecimal(12), 0, RoundingMode.DOWN)
            annualTax - history.withheld - history.previousEmployerWithheld
        }
    return IncomeTaxCalculation(
        rules.id,
        gross,
        taxAllowance,
        deductionAllowance,
        withholding,
        input.cashEarnings + taxAllowance + deductionAllowance - deductions - withholding,
        category,
        rate,
        annualNet,
        annualized,
        annualTaxable,
        annualTax,
    )
}
