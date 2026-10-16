package dev.fajar.hris.payroll.data.models

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class PayrollWorkAdjustmentRow(
    val id: UUID,
    val revision: Long,
    val workDate: LocalDate,
    val recordedAt: OffsetDateTime,
    val reason: String,
)
