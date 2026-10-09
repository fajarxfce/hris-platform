package dev.fajar.hris.payroll.data.models

data class PayrollPolicyData(
    val incomeTaxRuleId: String,
    val insuranceRuleId: String,
    val minimumMonthlyWage: String,
    val healthWageCap: String,
    val pensionWageCap: String,
    val contributionRounding: String,
    val reviewReferences: List<String>,
)
