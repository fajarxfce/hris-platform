package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollPolicySnapshotResponse(
    val version: Long,
    val appliedRevision: Long,
    val effectiveFrom: String,
    val effectiveUntil: String,
    val incomeTaxRuleId: String,
    val insuranceRuleId: String,
    val minimumMonthlyWage: String,
    val healthWageCap: String,
    val pensionWageCap: String,
    val contributionRounding: String,
    val reviewReferences: List<String>,
)
