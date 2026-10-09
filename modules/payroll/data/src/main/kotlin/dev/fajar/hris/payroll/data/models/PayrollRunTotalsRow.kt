package dev.fajar.hris.payroll.data.models

import java.math.BigDecimal

data class PayrollRunTotalsRow(
    val employeeCount: Int,
    val taxableGross: BigDecimal,
    val withheld: BigDecimal,
    val takeHome: BigDecimal,
)
