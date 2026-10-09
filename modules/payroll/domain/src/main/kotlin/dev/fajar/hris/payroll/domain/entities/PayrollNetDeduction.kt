package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class PayrollNetDeduction(val code: String, val name: String, val amount: BigDecimal)
