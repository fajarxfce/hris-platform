package dev.fajar.hris.payroll.data.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.*
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class PayrollClosedScheduleData(
    val kind: String,
    val workDate: LocalDate,
    val holidayId: UUID? = null,
)
