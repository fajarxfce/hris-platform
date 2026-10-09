package dev.fajar.hris.payroll.domain.policies

import java.math.BigDecimal

val PAYROLL_MAXIMUM_MONTHLY_AMOUNT: BigDecimal = BigDecimal("50000000000")
val PAYROLL_MAXIMUM_YEARLY_AMOUNT: BigDecimal = BigDecimal("600000000000")

fun validPayrollAmount(value: BigDecimal, yearly: Boolean = false): Boolean =
    value.scale() in 0..2 &&
        value.precision() <= 16 &&
        value.signum() >= 0 &&
        value <= if (yearly) PAYROLL_MAXIMUM_YEARLY_AMOUNT else PAYROLL_MAXIMUM_MONTHLY_AMOUNT
