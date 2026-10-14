package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

/** Signed changes to assessed entitlement, separately from actual bank settlement. */
data class PayrollAmendmentDifference(
    val taxableGross: BigDecimal = BigDecimal.ZERO,
    val withheld: BigDecimal = BigDecimal.ZERO,
    val takeHome: BigDecimal = BigDecimal.ZERO,
    val employeeDeductions: BigDecimal = BigDecimal.ZERO,
    val employerCost: BigDecimal = BigDecimal.ZERO,
)
