package dev.fajar.hris.payroll.data.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.*
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class PayrollClosedWorkData(
    val employeeId: UUID,
    val month: String,
    val days: List<PayrollClosedDayData>,
)
