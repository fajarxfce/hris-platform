package dev.fajar.hris.payroll.data.models

import java.time.*

data class PayrollPolicySnapshotData(
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
