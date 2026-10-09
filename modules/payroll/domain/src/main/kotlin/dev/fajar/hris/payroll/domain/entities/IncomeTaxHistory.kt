package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class IncomeTaxHistory(
    val taxableGross: BigDecimal = BigDecimal.ZERO,
    val retirementContributions: BigDecimal = BigDecimal.ZERO,
    val qualifiedDonations: BigDecimal = BigDecimal.ZERO,
    val withheld: BigDecimal = BigDecimal.ZERO,
    val employmentMonths: Int = 0,
    val previousEmployerNet: BigDecimal = BigDecimal.ZERO,
    val previousEmployerWithheld: BigDecimal = BigDecimal.ZERO,
)
