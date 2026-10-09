package dev.fajar.hris.payroll.delivery.responses

import java.time.*
import java.util.UUID

data class PayrollLeaveDayResponse(
    val requestId: UUID,
    val revision: Long,
    val date: LocalDate,
    val portion: String,
    val paid: Boolean,
)
