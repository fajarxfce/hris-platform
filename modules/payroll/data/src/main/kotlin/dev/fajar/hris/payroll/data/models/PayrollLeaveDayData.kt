package dev.fajar.hris.payroll.data.models

import java.time.*
import java.util.UUID

data class PayrollLeaveDayData(
    val requestId: UUID,
    val revision: Long,
    val date: LocalDate,
    val portion: String,
    val paid: Boolean,
)
