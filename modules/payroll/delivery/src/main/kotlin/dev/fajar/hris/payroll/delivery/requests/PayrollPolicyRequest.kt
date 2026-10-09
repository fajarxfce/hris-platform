package dev.fajar.hris.payroll.delivery.requests

import dev.fajar.hris.payroll.domain.entities.ContributionRounding
import java.time.YearMonth

data class PayrollPolicyRequest(
    val effectiveFrom: YearMonth,
    val effectiveUntil: YearMonth,
    val incomeTaxRuleId: String,
    val insuranceRuleId: String,
    val minimumMonthlyWage: String,
    val healthWageCap: String,
    val pensionWageCap: String,
    val contributionRounding: ContributionRounding,
    val reviewReferences: List<String>,
    val expectedVersion: Long? = null,
    val reason: String,
)
