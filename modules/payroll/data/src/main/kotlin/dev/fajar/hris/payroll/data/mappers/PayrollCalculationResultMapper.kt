package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import java.math.BigDecimal
import java.time.*

fun InsuranceContribution.toSnapshotData() =
    InsuranceContributionData(
        program = program.name,
        base = base.toPlainString(),
        employeeAmount = employeeAmount.toPlainString(),
        employerAmount = employerAmount.toPlainString(),
    )

fun InsuranceContributionData.toDomain() =
    InsuranceContribution(
        program = InsuranceProgram.valueOf(program),
        base = BigDecimal(base),
        employeeAmount = BigDecimal(employeeAmount),
        employerAmount = BigDecimal(employerAmount),
    )

fun IncomeTaxInput.toSnapshotData() =
    IncomeTaxInputData(
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
        history = history.toData(),
    )

fun IncomeTaxInputData.toDomain() =
    IncomeTaxInput(
        month = YearMonth.parse(month),
        residency = TaxResidency.valueOf(residency),
        ptkp = PtkpStatus.valueOf(ptkp),
        treatment = TaxTreatment.valueOf(treatment),
        cashEarnings = BigDecimal(cashEarnings),
        nonTaxableCash = BigDecimal(nonTaxableCash),
        nonCashTaxable = BigDecimal(nonCashTaxable),
        retirementContributions = BigDecimal(retirementContributions),
        qualifiedDonations = BigDecimal(qualifiedDonations),
        otherNetDeductions = BigDecimal(otherNetDeductions),
        finalPeriod = finalPeriod,
        subjectiveMonths = subjectiveMonths,
        history = history.toHistory(),
    )

fun IncomeTaxCalculation.toSnapshotData() =
    IncomeTaxCalculationData(
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

fun IncomeTaxCalculationData.toDomain() =
    IncomeTaxCalculation(
        ruleId = ruleId,
        taxableGross = BigDecimal(taxableGross),
        taxAllowance = BigDecimal(taxAllowance),
        deductionAllowance = BigDecimal(deductionAllowance),
        withheld = BigDecimal(withheld),
        takeHome = BigDecimal(takeHome),
        category = category?.let { value -> TerCategory.valueOf(value) },
        effectiveRate = effectiveRate?.let { value -> BigDecimal(value) },
        annualNet = annualNet?.let { value -> BigDecimal(value) },
        annualizedNet = annualizedNet?.let { value -> BigDecimal(value) },
        annualTaxable = annualTaxable?.let { value -> BigDecimal(value) },
        annualTax = annualTax?.let { value -> BigDecimal(value) },
    )

fun PayrollMonthlyCalculation.toSnapshotData() =
    PayrollMonthlyCalculationData(
        units = units.toSnapshotData(),
        earnings = java.util.List.copyOf(earnings.map { item -> item.toSnapshotData() }),
        overtime = java.util.List.copyOf(overtime.map { item -> item.toSnapshotData() }),
        holiday = holiday?.let { value -> value.toSnapshotData() },
        contributions = java.util.List.copyOf(contributions.map { item -> item.toSnapshotData() }),
        taxInput = taxInput.toSnapshotData(),
        tax = tax.toSnapshotData(),
        employeeDeductions = employeeDeductions.toPlainString(),
        employerCost = employerCost.toPlainString(),
    )

fun PayrollMonthlyCalculationData.toDomain() =
    PayrollMonthlyCalculation(
        units = units.toDomain(),
        earnings = java.util.List.copyOf(earnings.map { item -> item.toDomain() }),
        overtime = java.util.List.copyOf(overtime.map { item -> item.toDomain() }),
        holiday = holiday?.let { value -> value.toDomain() },
        contributions = java.util.List.copyOf(contributions.map { item -> item.toDomain() }),
        taxInput = taxInput.toDomain(),
        tax = tax.toDomain(),
        employeeDeductions = BigDecimal(employeeDeductions),
        employerCost = BigDecimal(employerCost),
    )
