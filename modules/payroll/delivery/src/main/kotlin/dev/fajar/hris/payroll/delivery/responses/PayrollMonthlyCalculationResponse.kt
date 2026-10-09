package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollMonthlyCalculationResponse(
    val units: PayrollPayUnitsResponse,
    val earnings: List<PayrollEarningLineResponse>,
    val overtime: List<PayrollOvertimeDayResponse>,
    val holiday: HolidayAllowanceResponse?,
    val contributions: List<InsuranceContributionResponse>,
    val taxInput: IncomeTaxInputResponse,
    val tax: IncomeTaxCalculationResponse,
    val employeeDeductions: String,
    val employerCost: String,
)
