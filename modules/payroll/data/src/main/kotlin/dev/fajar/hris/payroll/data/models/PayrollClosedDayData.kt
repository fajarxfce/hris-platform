package dev.fajar.hris.payroll.data.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class PayrollClosedDayData(
    val workDate: LocalDate,
    val fact: String,
    val schedule: PayrollClosedScheduleData,
    val overtime: List<PayrollClosedOvertimeData> = emptyList(),
)
