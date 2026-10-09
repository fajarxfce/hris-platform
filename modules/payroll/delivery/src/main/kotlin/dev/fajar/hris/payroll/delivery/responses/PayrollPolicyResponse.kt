package dev.fajar.hris.payroll.delivery.responses

import java.time.YearMonth

data class PayrollPolicyResponse(
    val version: Long,
    val appliedRevision: Long,
    val effectiveFrom: YearMonth,
    val effectiveUntil: YearMonth,
    val currency: String,
    val incomeTaxRuleId: String,
    val insuranceRuleId: String,
    val minimumMonthlyWage: String,
    val healthWageCap: String,
    val pensionWageCap: String,
    val contributionRounding: String,
    val reviewReferences: List<String>,
)
