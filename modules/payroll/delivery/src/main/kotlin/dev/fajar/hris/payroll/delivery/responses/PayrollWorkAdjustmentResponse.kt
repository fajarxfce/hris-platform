package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PayrollWorkAdjustmentResponse(
    val id: UUID,
    val revision: Long,
    val workDate: LocalDate,
    val recordedAt: Instant,
    val reason: String,
)
