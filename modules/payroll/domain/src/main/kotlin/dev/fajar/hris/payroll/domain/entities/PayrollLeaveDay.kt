package dev.fajar.hris.payroll.domain.entities

import java.time.LocalDate
import java.util.UUID

/** Only approved, uncancelled leave enters this calculation projection. */
data class PayrollLeaveDay(
    val requestId: UUID,
    val revision: Long,
    val date: LocalDate,
    val portion: PayrollDayPortion,
    val paid: Boolean,
)
