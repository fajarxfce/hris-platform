package dev.fajar.hris.payroll.delivery.responses

data class PayrollPayslipResponse(
    val summary: PayrollPayslipSummaryResponse,
    val companyCode: String,
    val companyName: String,
    val calculation: PayrollMonthlyCalculationResponse,
)
