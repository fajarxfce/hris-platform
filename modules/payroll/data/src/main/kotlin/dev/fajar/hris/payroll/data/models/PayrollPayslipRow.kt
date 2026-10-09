package dev.fajar.hris.payroll.data.models

data class PayrollPayslipRow(
    val summary: PayrollPayslipSummaryRow,
    val companyCode: String,
    val companyName: String,
    val calculation: String,
)
