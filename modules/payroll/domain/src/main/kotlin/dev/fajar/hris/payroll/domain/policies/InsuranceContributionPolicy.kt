package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.math.RoundingMode

fun calculateInsuranceContributions(
    policy: PayrollPolicy,
    terms: CompensationTerms,
): Result<List<InsuranceContribution>> {
    if (
        policy.insuranceRuleId != INDONESIAN_INSURANCE_PU_V1 ||
            !validPayrollAmount(terms.insuranceWage) ||
            terms.insuranceWage.signum() <= 0 ||
            terms.additionalHealthDependents !in 0..5 ||
            listOf(policy.minimumMonthlyWage, policy.healthWageCap, policy.pensionWageCap).any {
                !validPayrollAmount(it) || it.signum() <= 0
            } ||
            policy.minimumMonthlyWage > policy.healthWageCap
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_contributions"))
    val rounding = RoundingMode.valueOf(policy.contributionRounding.name)
    return Result.Success(
        terms.insurancePrograms
            .sortedBy { it.ordinal }
            .map { program ->
                val base =
                    when (program) {
                        InsuranceProgram.HEALTH ->
                            terms.insuranceWage
                                .max(policy.minimumMonthlyWage)
                                .min(policy.healthWageCap)
                        InsuranceProgram.PENSION -> terms.insuranceWage.min(policy.pensionWageCap)
                        else -> terms.insuranceWage
                    }
                val employeeRate =
                    when (program) {
                        InsuranceProgram.HEALTH ->
                            BigDecimal("0.01")
                                .multiply(BigDecimal(1 + terms.additionalHealthDependents))
                        InsuranceProgram.OLD_AGE -> BigDecimal("0.02")
                        InsuranceProgram.PENSION -> BigDecimal("0.01")
                        else -> BigDecimal.ZERO
                    }
                val employerRate =
                    when (program) {
                        InsuranceProgram.HEALTH -> BigDecimal("0.04")
                        InsuranceProgram.OLD_AGE -> BigDecimal("0.037")
                        InsuranceProgram.PENSION -> BigDecimal("0.02")
                        InsuranceProgram.DEATH -> BigDecimal("0.003")
                        InsuranceProgram.ACCIDENT ->
                            when (terms.accidentRisk) {
                                AccidentRisk.VERY_LOW -> BigDecimal("0.0024")
                                AccidentRisk.LOW -> BigDecimal("0.0054")
                                AccidentRisk.MEDIUM -> BigDecimal("0.0089")
                                AccidentRisk.HIGH -> BigDecimal("0.0127")
                                AccidentRisk.VERY_HIGH -> BigDecimal("0.0174")
                            }
                    }
                InsuranceContribution(
                    program,
                    base,
                    base.multiply(employeeRate).setScale(0, rounding),
                    base.multiply(employerRate).setScale(0, rounding),
                )
            }
    )
}
