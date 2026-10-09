package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.YearMonth

const val INDONESIAN_INSURANCE_PU_V1 = "ID-BPJS-PU-v1"

fun requireEffectivePayrollPolicy(policy: PayrollPolicy?, month: YearMonth): Result<PayrollPolicy> =
    when {
        policy == null -> Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_policy_not_found"))
        month < policy.effectiveFrom || month > policy.effectiveUntil ->
            Result.Failed(
                Failure(
                    FailureKind.CONFLICT,
                    "payroll_policy_not_effective",
                    parameters =
                        mapOf(
                            "validFrom" to policy.effectiveFrom.toString(),
                            "validUntil" to policy.effectiveUntil.toString(),
                        ),
                )
            )
        else -> Result.Success(policy)
    }

fun validatePayrollPolicy(policy: PayrollPolicy, reason: String): Result<Unit> {
    val fields = linkedMapOf<String, String>()
    if (policy.version !in 0..999 || policy.appliedRevision !in 0..999)
        fields["expectedVersion"] = "out_of_range"
    if (policy.effectiveFrom.year !in 2024..2100) fields["effectiveFrom"] = "out_of_range"
    if (
        policy.effectiveUntil < policy.effectiveFrom ||
            policy.effectiveUntil.year != policy.effectiveFrom.year
    )
        fields["effectiveUntil"] = "invalid_period"
    val rules = incomeTaxRules(policy.incomeTaxRuleId)
    if (rules == null || policy.effectiveFrom.atDay(1) < rules.effectiveFrom)
        fields["incomeTaxRuleId"] = "unavailable"
    if (policy.insuranceRuleId != INDONESIAN_INSURANCE_PU_V1)
        fields["insuranceRuleId"] = "unavailable"
    for ((field, value) in
        mapOf(
            "minimumMonthlyWage" to policy.minimumMonthlyWage,
            "healthWageCap" to policy.healthWageCap,
            "pensionWageCap" to policy.pensionWageCap,
        )) if (!validPayrollAmount(value) || value.signum() <= 0) fields[field] = "out_of_range"
    if (policy.reviewReferences.size !in 1..10) fields["reviewReferences"] = "invalid_count"
    for ((index, reference) in policy.reviewReferences.take(10).withIndex()) if (
        reference.length !in 9..500 ||
            !reference.startsWith("https://") ||
            reference.any(Char::isWhitespace) ||
            reference.any(Char::isISOControl)
    )
        fields["reviewReferences[$index]"] = "invalid"
    if (reason.isBlank() || reason.length > 1000) fields["reason"] = "invalid"
    if (fields.isNotEmpty())
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_payroll_policy", fields = fields)
        )
    if (policy.minimumMonthlyWage > policy.healthWageCap)
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_payroll_policy",
                fields = mapOf("healthWageCap" to "below_minimum"),
            )
        )
    return Result.Success(Unit)
}
