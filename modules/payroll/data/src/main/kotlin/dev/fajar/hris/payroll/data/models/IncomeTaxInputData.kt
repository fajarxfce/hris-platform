package dev.fajar.hris.payroll.data.models

import java.time.*

data class IncomeTaxInputData(
    val month: String,
    val residency: String,
    val ptkp: String,
    val treatment: String,
    val cashEarnings: String,
    val nonTaxableCash: String,
    val nonCashTaxable: String,
    val retirementContributions: String,
    val qualifiedDonations: String,
    val otherNetDeductions: String,
    val finalPeriod: Boolean,
    val subjectiveMonths: Int,
    val history: IncomeTaxHistoryData,
)
