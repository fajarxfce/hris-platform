package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class IncomeTaxInputResponse(
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
    val history: IncomeTaxHistoryResponse,
)
