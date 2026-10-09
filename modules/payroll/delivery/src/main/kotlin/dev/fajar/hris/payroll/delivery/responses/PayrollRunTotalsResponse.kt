package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollRunTotalsResponse(
    val employeeCount: Int,
    val taxableGross: String,
    val withheld: String,
    val takeHome: String,
    val currency: String,
)
