package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class PayrollMonthlyCalculation(
    val units: PayrollPayUnits,
    val earnings: List<PayrollEarningLine>,
    val overtime: List<PayrollOvertimeDay>,
    val holiday: HolidayAllowance?,
    val contributions: List<InsuranceContribution>,
    val taxInput: IncomeTaxInput,
    val tax: IncomeTaxCalculation,
    val employeeDeductions: BigDecimal,
    val employerCost: BigDecimal,
)
