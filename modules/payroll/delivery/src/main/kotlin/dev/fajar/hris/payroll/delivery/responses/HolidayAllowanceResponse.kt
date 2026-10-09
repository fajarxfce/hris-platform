package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class HolidayAllowanceResponse(
    val ruleId: String,
    val holidayDate: LocalDate,
    val dueDate: LocalDate,
    val service: HolidayServiceResponse,
    val monthlyWage: String,
    val statutoryAmount: String,
    val employerTopUp: String,
    val amount: String,
    val rounding: String,
)
