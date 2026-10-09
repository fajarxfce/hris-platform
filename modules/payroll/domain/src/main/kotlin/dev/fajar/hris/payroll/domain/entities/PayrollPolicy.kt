package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.YearMonth

data class PayrollPolicy(
    val version: Long,
    val appliedRevision: Long,
    val effectiveFrom: YearMonth,
    val effectiveUntil: YearMonth,
    val incomeTaxRuleId: String,
    val insuranceRuleId: String,
    val minimumMonthlyWage: BigDecimal,
    val healthWageCap: BigDecimal,
    val pensionWageCap: BigDecimal,
    val contributionRounding: ContributionRounding,
    val reviewReferences: List<String>,
)
