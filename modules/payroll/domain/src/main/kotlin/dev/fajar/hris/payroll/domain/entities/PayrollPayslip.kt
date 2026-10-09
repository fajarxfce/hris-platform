package dev.fajar.hris.payroll.domain.entities

data class PayrollPayslip(
    val summary: PayrollPayslipSummary,
    val companyCode: String,
    val companyName: String,
    val calculation: PayrollMonthlyCalculation,
)
