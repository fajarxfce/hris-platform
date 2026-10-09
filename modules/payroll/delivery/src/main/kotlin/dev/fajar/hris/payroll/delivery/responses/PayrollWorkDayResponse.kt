package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollWorkDayResponse(
    val date: LocalDate,
    val schedule: String,
    val attendance: String,
    val officialHoliday: Boolean,
    val overtime: List<PayrollOvertimeEvidenceResponse>,
)
