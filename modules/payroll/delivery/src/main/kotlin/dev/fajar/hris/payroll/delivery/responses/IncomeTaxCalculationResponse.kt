package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class IncomeTaxCalculationResponse(
    val ruleId: String,
    val taxableGross: String,
    val taxAllowance: String,
    val deductionAllowance: String,
    val withheld: String,
    val takeHome: String,
    val category: String?,
    val effectiveRate: String?,
    val annualNet: String?,
    val annualizedNet: String?,
    val annualTaxable: String?,
    val annualTax: String?,
)
