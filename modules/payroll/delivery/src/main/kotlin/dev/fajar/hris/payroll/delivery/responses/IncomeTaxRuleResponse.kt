package dev.fajar.hris.payroll.delivery.responses

import java.time.LocalDate

data class IncomeTaxRuleResponse(
    val id: String,
    val effectiveFrom: LocalDate,
    val effectiveUntil: LocalDate?,
    val sources: List<String>,
    val monthly: Map<String, List<IncomeTaxBandResponse>>,
    val progressive: List<IncomeTaxBandResponse>,
    val allowances: Map<String, String>,
    val categories: Map<String, String>,
    val jobExpenseRate: String,
    val monthlyJobExpenseCap: String,
    val nonResidentRate: String,
)
