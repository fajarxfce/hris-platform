package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import java.time.*

fun InsuranceContribution.toCalculationResponse() =
    InsuranceContributionResponse(
        program = program.name,
        base = base.toPlainString(),
        employeeAmount = employeeAmount.toPlainString(),
        employerAmount = employerAmount.toPlainString(),
    )

fun IncomeTaxInput.toCalculationResponse() =
    IncomeTaxInputResponse(
        month = month.toString(),
        residency = residency.name,
        ptkp = ptkp.name,
        treatment = treatment.name,
        cashEarnings = cashEarnings.toPlainString(),
        nonTaxableCash = nonTaxableCash.toPlainString(),
        nonCashTaxable = nonCashTaxable.toPlainString(),
        retirementContributions = retirementContributions.toPlainString(),
        qualifiedDonations = qualifiedDonations.toPlainString(),
        otherNetDeductions = otherNetDeductions.toPlainString(),
        finalPeriod = finalPeriod,
        subjectiveMonths = subjectiveMonths,
        history = history.toResponse(),
    )

fun IncomeTaxCalculation.toCalculationResponse() =
    IncomeTaxCalculationResponse(
        ruleId = ruleId,
        taxableGross = taxableGross.toPlainString(),
        taxAllowance = taxAllowance.toPlainString(),
        deductionAllowance = deductionAllowance.toPlainString(),
        withheld = withheld.toPlainString(),
        takeHome = takeHome.toPlainString(),
        category = category?.let { value -> value.name },
        effectiveRate = effectiveRate?.let { value -> value.toPlainString() },
        annualNet = annualNet?.let { value -> value.toPlainString() },
        annualizedNet = annualizedNet?.let { value -> value.toPlainString() },
        annualTaxable = annualTaxable?.let { value -> value.toPlainString() },
        annualTax = annualTax?.let { value -> value.toPlainString() },
    )

fun PayrollMonthlyCalculation.toCalculationResponse() =
    PayrollMonthlyCalculationResponse(
        units = units.toCalculationResponse(),
        earnings = java.util.List.copyOf(earnings.map { item -> item.toCalculationResponse() }),
        overtime = java.util.List.copyOf(overtime.map { item -> item.toCalculationResponse() }),
        holiday = holiday?.let { value -> value.toCalculationResponse() },
        contributions =
            java.util.List.copyOf(contributions.map { item -> item.toCalculationResponse() }),
        taxInput = taxInput.toCalculationResponse(),
        tax = tax.toCalculationResponse(),
        employeeDeductions = employeeDeductions.toPlainString(),
        employerCost = employerCost.toPlainString(),
    )
