package dev.fajar.hris.payroll.data.models

import java.time.*

data class IncomeTaxCalculationData(
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
