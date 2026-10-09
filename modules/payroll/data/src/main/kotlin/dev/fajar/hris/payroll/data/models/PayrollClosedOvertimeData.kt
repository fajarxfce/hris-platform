package dev.fajar.hris.payroll.data.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.*
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class PayrollClosedOvertimeData(
    val requestId: UUID,
    val revision: Long,
    val approvedMinutes: Int,
)
