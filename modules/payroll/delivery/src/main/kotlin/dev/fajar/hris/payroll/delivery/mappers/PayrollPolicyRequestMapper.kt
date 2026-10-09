package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.http.decimalAmount
import dev.fajar.hris.payroll.delivery.requests.PayrollPolicyRequest
import dev.fajar.hris.payroll.domain.entities.PayrollPolicy

fun PayrollPolicyRequest.toPolicy() =
    PayrollPolicy(
        expectedVersion ?: 0,
        expectedVersion ?: 0,
        effectiveFrom,
        effectiveUntil,
        incomeTaxRuleId,
        insuranceRuleId,
        decimalAmount(minimumMonthlyWage),
        decimalAmount(healthWageCap),
        decimalAmount(pensionWageCap),
        contributionRounding,
        java.util.List.copyOf(reviewReferences),
    )
