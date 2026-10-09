package dev.fajar.hris.payroll.data.models

import java.time.*

data class PayrollMonthlyCalculationData(
    val units: PayrollPayUnitsData,
    val earnings: List<PayrollEarningLineData>,
    val overtime: List<PayrollOvertimeDayData>,
    val holiday: HolidayAllowanceData?,
    val contributions: List<InsuranceContributionData>,
    val taxInput: IncomeTaxInputData,
    val tax: IncomeTaxCalculationData,
    val employeeDeductions: String,
    val employerCost: String,
)
