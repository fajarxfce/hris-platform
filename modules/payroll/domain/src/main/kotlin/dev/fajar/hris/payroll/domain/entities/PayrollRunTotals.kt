package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.time.*

data class PayrollRunTotals(
    val employeeCount: Int,
    val taxableGross: BigDecimal,
    val withheld: BigDecimal,
    val takeHome: BigDecimal,
)
