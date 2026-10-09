package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class PayrollInputTerms(
    val earnings: List<PayrollVariableEarning>,
    val deductions: List<PayrollNetDeduction>,
    val nonCashTaxable: BigDecimal,
    val scheduledMonthUnits: BigDecimal?,
    val dayResolutions: List<PayrollDayResolution>,
    val holidayAllowance: PayrollHolidayInput?,
    val reviewReference: String,
)
