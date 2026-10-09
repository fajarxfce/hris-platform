package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.YearMonth

data class IncomeTaxInput(
    val month: YearMonth,
    val residency: TaxResidency,
    val ptkp: PtkpStatus,
    val treatment: TaxTreatment,
    val cashEarnings: BigDecimal,
    val nonTaxableCash: BigDecimal = BigDecimal.ZERO,
    val nonCashTaxable: BigDecimal = BigDecimal.ZERO,
    val retirementContributions: BigDecimal = BigDecimal.ZERO,
    val qualifiedDonations: BigDecimal = BigDecimal.ZERO,
    val otherNetDeductions: BigDecimal = BigDecimal.ZERO,
    val finalPeriod: Boolean = false,
    val subjectiveMonths: Int = 12,
    val history: IncomeTaxHistory = IncomeTaxHistory(),
)
