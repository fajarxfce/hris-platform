package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class IncomeTaxCalculation(
    val ruleId: String,
    val taxableGross: BigDecimal,
    val taxAllowance: BigDecimal,
    val deductionAllowance: BigDecimal,
    val withheld: BigDecimal,
    val takeHome: BigDecimal,
    val category: TerCategory?,
    val effectiveRate: BigDecimal?,
    val annualNet: BigDecimal?,
    val annualizedNet: BigDecimal?,
    val annualTaxable: BigDecimal?,
    val annualTax: BigDecimal?,
)
