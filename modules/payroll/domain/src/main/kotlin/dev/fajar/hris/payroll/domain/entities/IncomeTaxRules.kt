package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.LocalDate

data class IncomeTaxRules(
    val id: String,
    val effectiveFrom: LocalDate,
    val effectiveUntil: LocalDate?,
    val sources: List<String>,
    val monthly: Map<TerCategory, List<IncomeTaxBand>>,
    val progressive: List<IncomeTaxBand>,
    val allowances: Map<PtkpStatus, BigDecimal>,
    val categories: Map<PtkpStatus, TerCategory>,
    val jobExpenseRate: BigDecimal,
    val monthlyJobExpenseCap: BigDecimal,
    val nonResidentRate: BigDecimal,
)
