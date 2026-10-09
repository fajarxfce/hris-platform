package dev.fajar.hris.payroll.data.models

import java.time.*

data class PayrollWorkDayData(
    val date: LocalDate,
    val schedule: String,
    val attendance: String,
    val officialHoliday: Boolean,
    val overtime: List<PayrollOvertimeEvidenceData>,
)
