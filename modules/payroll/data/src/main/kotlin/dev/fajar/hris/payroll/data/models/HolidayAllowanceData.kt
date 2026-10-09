package dev.fajar.hris.payroll.data.models

import java.time.*

data class HolidayAllowanceData(
    val ruleId: String,
    val holidayDate: LocalDate,
    val dueDate: LocalDate,
    val service: HolidayServiceData,
    val monthlyWage: String,
    val statutoryAmount: String,
    val employerTopUp: String,
    val amount: String,
    val rounding: String,
)
