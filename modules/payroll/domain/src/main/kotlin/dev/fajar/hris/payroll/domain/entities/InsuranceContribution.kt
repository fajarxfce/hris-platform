package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class InsuranceContribution(
    val program: InsuranceProgram,
    val base: BigDecimal,
    val employeeAmount: BigDecimal,
    val employerAmount: BigDecimal,
)
